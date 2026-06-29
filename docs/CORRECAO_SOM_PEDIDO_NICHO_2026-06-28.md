# Correção do som de novo pedido — nicho do backend + sirene indevida — 2026-06-28

Dois bugs encontrados na revisão (banner/som/pedidos) que impactavam o alerta
sonoro de novo pedido. Banner ficou sem bugs significativos.

---

## Bug 1 — Pedido NORMAL nunca disparava push (som com app fechado não tocava)

**Sintoma:** com o app fechado, o prestador não recebia push/som para serviços
comuns — só funcionava com o app aberto (caminho in-app) e para guincho.

**Causa raiz:** o backend `provider-notification.service.js` derivava o nicho do
pedido apenas de `serviceCategory`:

```js
const niche = (order.serviceCategory || order.service_category_name || '').trim();
if (!niche) return; // "Pedido sem nicho, ignorando notificação"
```

Mas pedidos **normais** (criados em `CreateOrderActivity` e no checkout do
carrinho `FirebaseCartManager`) gravam o nicho em **`serviceName`**
(`serviceType` é o serviço específico). Só o **guincho** (`TowingOrderActivity`)
grava `serviceCategory`. Logo, para todo pedido comum o `niche` ficava vazio e o
backend **abortava sem enviar FCM a ninguém**. O caminho in-app não era afetado
porque casa por `serviceType/serviceName` via `ServiceNicheCatalog`.

**Correção** (`backend/src/services/provider-notification.service.js`): incluir
`serviceName` no fallback do nicho:

```js
const niche = (order.serviceCategory || order.service_category_name || order.serviceName || '').trim();
```

A correspondência é com `providers.services` (array de **nichos**), e
`serviceName` contém exatamente o nicho — então o match passa a funcionar para
pedidos normais. **Escopo: backend (Render).**

---

## Bug 2 — Mensagem de chat com a palavra "pedido" disparava a sirene

**Sintoma:** mensagens de chat/central que continham "pedido" no título/corpo
(ex.: "Cadê meu **pedido**?") faziam o app tocar a **sirene contínua** de novo
pedido — inclusive para prestador indisponível.

**Causa raiz:** em `FirebaseMessagingService`, a classificação era frouxa:

```kotlin
val isOrderNotification = type.equals("order") ||
    title.contains("pedido", true) || message.contains("pedido", true)
```

Uma mensagem de chat (`type=chat_message`/`central_message`) com `orderId` e a
palavra "pedido" caía no bloco da sirene contínua, que **não excluía**
`isMessageType` e **ignorava** o gate de disponibilidade (que só checa `type==order`).

**Correção** (`FirebaseMessagingService.kt`): a sirene só dispara em alertas reais
de novo pedido:

```kotlin
val isOrderNotification = type.equals("order", ignoreCase = true) && !isMessageType
```

O backend já envia `data.type = "order"` nos alertas de pedido, então a checagem
por tipo é suficiente e correta. Mensagens de chat continuam no canal de
mensagens normal. **Escopo: app (Kotlin) — exige APK novo (NÃO gerado nesta
rodada, a pedido).**

---

## Publicação

| Plataforma | Mudou? | Ação |
|---|---|---|
| **GitHub** (Delta `main` + alvaro) | Sim | push |
| **Render** (backend pagamentos) | Sim (Bug 1) | deploy manual (autoDeploy OFF) |
| **Firebase** | Não | nada a publicar (sem mudança de regras/índices) |
| **APK** | Bug 2 pendente | **não gerado ainda** (a pedido) |

**Observação:** o Bug 1 (Render) destrava o push para pedidos normais já com o
APK atual — desde que o prestador tenha um token FCM válido (correção de token de
sessões anteriores exige APK novo + relogin). O Bug 2 só tem efeito quando o APK
novo for gerado e instalado.

Validação: `node -c` do backend OK; `compileDebugKotlin` = BUILD SUCCESSFUL.
