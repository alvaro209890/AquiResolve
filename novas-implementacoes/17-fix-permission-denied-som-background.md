# Correções: Permission Denied + Som Contínuo + Foreground Service

**Data:** 2026-06-27
**Commits:** agrupados abaixo

---

## Problemas Corrigidos

### 1. Permission Denied ao aceitar/rejeitar pedido

**Causa:** As regras do Firestore não permitiam que um prestador adicionasse `rejectedBy` a um pedido em distribuição. As 3 funções de update (`validClientOrderUpdate`, `validProviderAcceptUpdate`, `validProviderOrderUpdate`) não cobriam esse caso.

**Correção:** Adicionada nova função `validProviderRejectUpdate()` no `firestore.rules`:
```javascript
function validProviderRejectUpdate() {
  return isSignedIn()
    && resource.data.status in ['distributing', 'pending', 'available']
    && request.resource.data.diff(resource.data).affectedKeys()
        .hasOnly(['rejectedBy']);
}
```
E adicionada ao `allow update` dos pedidos. Regras deployadas via `firebase-tools`.

**Arquivo:** `firestore.rules`

### 2. Som parava ao abrir tela de pedidos

**Causa:** `stopMonitoring()` chamava `NewOrderSoundHelper.stopSound()` indiscriminadamente. O `refreshMonitoring()` → `attachListenerIfProviderApproved()` → `stopMonitoring("reiniciar listener")` estava matando o som toda vez que uma Activity abria/fechava.

**Correção:** Adicionado parâmetro `keepSound: Boolean = false` ao `stopMonitoring()`. O refresh agora chama `stopMonitoring("reiniciar listener", keepSound = true)` preservando os sons ativos.

**Arquivo:** `ProviderNewOrderAlertManager.kt`

### 3. Som não tocava com app fechado (background)

**Causa:** O `MediaPlayer` e o listener Firestore do `ProviderNewOrderAlertManager` dependiam do processo do app estar vivo. Android pode matar o processo em background.

**Correção:** Criado `AlertForegroundService` — um Foreground Service leve com notificação persistente "Monitorando novos pedidos..." que mantém o processo vivo enquanto há sons ativos. Inicia automaticamente no primeiro `startContinuousPlay()` e para no último `stopSound()`.

**Novo arquivo:** `AlertForegroundService.kt`
**Registrado em:** `AndroidManifest.xml` com `foregroundServiceType="dataSync"`

---

## Fluxo Completo Atualizado

```
1. Cliente cria pedido → status: "distributing"
2. ProviderNewOrderAlertManager (listener Firestore) detecta
3. AlertForegroundService inicia (notificação "Monitorando novos pedidos...")
4. Som contínuo começa (MediaPlayer em loop com ApplicationContext)
5. Notificação heads-up aparece com [Aceitar] [Rejeitar]
   └─ Rejeitar → RejectOrderReceiver → arrayUnion no rejectedBy (permitido pela nova regra!)
   └─ Aceitar → OrderDetailsActivity → acceptOrderAsProvider (permitido pelo validProviderAcceptUpdate)
6. Quando pedido é aceito (status→assigned) ou expira → som para → serviço para
7. App pode estar em background — Foreground Service mantém processo vivo
```

## Arquivos Modificados

| Arquivo | Mudança |
|---------|---------|
| `firestore.rules` | +`validProviderRejectUpdate()` |
| `ProviderNewOrderAlertManager.kt` | +`keepSound` param no `stopMonitoring` |
| `NewOrderSoundHelper.kt` | +`import AlertForegroundService`, +context store, +Foreground Service lifecycle |
| `AlertForegroundService.kt` | **novo** — Foreground Service para background |
| `AndroidManifest.xml` | +registro `AlertForegroundService` |

## Regras Firestore

Deploy realizado: `npx firebase-tools deploy --only firestore:rules`
Projeto: `aplicativoservico-143c2`
Status: ✅ sucesso
