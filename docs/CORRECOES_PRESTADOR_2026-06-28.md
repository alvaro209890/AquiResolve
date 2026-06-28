# Correções do app do Prestador — 2026-06-28

Sessão focada em três frentes do lado do **prestador**: banner da Home, aceite de
pedido (PERMISSION_DENIED), alerta sonoro de novo pedido com o app fechado, e o
**novo layout de pedidos na Home** (cards). Tudo abaixo está validado e publicado
onde se aplica.

---

## 1. Banner do prestador não aparecia

**Sintoma:** a Home do prestador nunca mostrava o carrossel de banners.

**Causa raiz:** a coleção `provider_banners` estava **vazia** em produção (0 docs).
O app esconde a seção (`sectionBanners` nasce `GONE`) quando não há banners, então
nada aparecia. App, regras (`provider_banners` = `read: isSignedIn()` / `write:false`),
API (`/api/provider-banners`) e página do painel (`/dashboard/configuracoes/banners-prestador`)
já estavam corretos.

**Correção:** novo script `dashboard_admin/scripts/seed-provider-banners.mjs`
(espelha `seed-banners.mjs`; `actionType` válido p/ prestador é `niche|service|url|none`,
sem `cashback`) e 3 banners semeados em produção. **Não exige APK novo** — é dado,
lido a cada abertura da Home.

---

## 2. Não era possível aceitar pedido (PERMISSION_DENIED)

**Sintoma:** ao aceitar qualquer pedido, o prestador recebia "erro ao aceitar" e
o Firestore retornava `PERMISSION_DENIED`.

**Causa raiz:** a regra `validProviderAcceptUpdate` lia o campo direto:

```
&& (resource.data.assignedProvider == null || resource.data.assignedProvider == '')
```

Pedidos novos **não têm** o campo `assignedProvider` (o `validOrderCreate` não o
cria). Nas regras do Firestore, **acessar um campo ausente por notação de ponto
gera erro → a regra nega**. Como todo pedido em distribuição está sem
`assignedProvider`, **todo** aceite caía em PERMISSION_DENIED. Era exatamente o
mesmo bug já corrigido na regra de **recusa** (`validProviderRejectUpdate`), que
desde antes usa `.get(...)`.

**Correção** (`firestore.rules` + cópia `dashboard_admin/firestore.rules`):

```
&& (resource.data.get('assignedProvider', null) == null
    || resource.data.get('assignedProvider', null) == '')
```

**Validação ponta a ponta** (REST, autenticado como prestador, sem tocar em dados
reais — pedido de teste criado e apagado): **antes HTTP 403**, **depois HTTP 200**.
Regra **publicada em produção** (ruleset `bba9183a-…`). **Correção de regra → vale
para o APK já instalado, sem APK novo.**

---

## 3. Som de novo pedido só tocava com o app aberto

O prestador devia ouvir o alerta sonoro mesmo com o app **completamente fechado**.
Eram **dois** problemas somados:

### 3a. Backend enviava FCM com bloco `notification`
Com o app morto/background, uma mensagem FCM que contém `notification` é exibida
pela **bandeja do sistema** e o `onMessageReceived` do app **NÃO roda** — então o
som contínuo (`AlertForegroundService` + `NewOrderSoundHelper.startContinuousPlay`)
nunca disparava.

**Correção** (`backend/src/services/provider-notification.service.js`): mensagem
**data-only** (title/body movidos para `data`, sem bloco `notification`) +
`android.priority = high`. Assim o `onMessageReceived` roda mesmo com o app
fechado e o app monta a própria notificação + inicia o som. **Deployado no Render**
(autoDeploy OFF → deploy manual via API; live, commit verificado).

### 3b. O app salvava o token errado
`FirebaseNotificationManager.saveUserToken` salvava em `fcm_tokens` o token do
**Firebase Installations (FIS)** — um JWT de auth interno — em vez do **token de
registro FCM**. Comprovado em produção: o único token salvo era `eyJhbGciOiJF…`
(JWT). O backend **não conseguia entregar push nenhum** a esse token.

**Correção** (`FirebaseNotificationManager.kt`): usar
`FirebaseMessaging.getInstance().token`; novo `saveToken(uid, token)` e o
`onNewToken` do `FirebaseMessagingService` passa a persistir o token recebido.
**Exige APK novo + relogin** (o `saveUserToken` roda no login e regrava o token
correto).

> Resumo: 3a sozinho não basta enquanto os tokens forem inválidos; 3b corrige a
> entrega. Ambos juntos fazem o som tocar com o app fechado.

---

## 4. Novo layout de pedidos na Home do prestador

**Antes:** a seção "Pedidos Disponíveis" mostrava só um **texto-resumo** dentro de
um card.

**Agora:** lista de **cards** (RecyclerView), um por pedido, com:
- Tipo de serviço + chip de status;
- Destaque "💰 Você ganha: R$ X" (só a comissão do prestador, nunca o valor do cliente);
- Cliente (ícone), endereço (ícone), descrição (2 linhas), data (ícone);
- Dois botões: **"Ver pedido"** (abre os detalhes, onde o prestador aceita) e
  **"Rejeitar"** (recusa só para ele — `arrayUnion(uid)` em `rejectedBy`).
- Badge com a contagem de pedidos e botão **"Ver todos os pedidos"** quando há mais
  que o preview (4 na Home).

**Arquivos:**
- `res/layout/item_provider_order.xml` — card redesenhado (ganho em destaque,
  ícones, divisória, botões "Ver pedido"/"Rejeitar").
- `adapters/ProviderOrdersAdapter.kt` — removido o `onAcceptOrder` (o aceite agora
  é nos detalhes); `btnViewOrder` → detalhes; `btnReject` visível só enquanto o
  pedido é recusável.
- `res/layout/activity_provider_home.xml` — `rvAvailableOrders` + estado vazio
  (`cardNoOrders`/`tvNoAvailableOrders`) + `tvAvailableOrdersCount` + `btnSeeAllOrders`.
- `ProviderHomeActivity.kt` — `setupAvailableOrdersList()`, `openOrderDetails()`,
  `rejectAvailableOrder()`, e `updateAvailableOrdersSummary()` reescrito para
  popular os cards e alternar o estado vazio.
- `ProviderOrdersFragment.kt` — ajustado à nova assinatura do adapter.

Mudança de **código** → exige **APK novo**. `./gradlew compileDebugKotlin` = BUILD
SUCCESSFUL.

---

## Estado final

| Item | Onde | Precisa APK novo? |
|---|---|---|
| Banner do prestador | Dado em `provider_banners` (semeado) | Não |
| Aceite (PERMISSION_DENIED) | Regra publicada em produção | Não |
| Som app fechado — backend data-only | Render (deployado) | Não (backend) |
| Som app fechado — token FCM | `FirebaseNotificationManager.kt` | **Sim** + relogin |
| Layout de pedidos na Home | App (layout + Kotlin) | **Sim** |
