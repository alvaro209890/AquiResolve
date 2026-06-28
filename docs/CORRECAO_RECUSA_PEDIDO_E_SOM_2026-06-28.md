# Correção — Recusa de pedido por prestador + som de alerta (2026-06-28)

## Sintomas relatados
1. O som de alerta de novo pedido **não deveria parar** até que **algum prestador aceite**, ou parar **só para o prestador X** se ele **recusar**.
2. Quando **um** prestador recusa, o pedido **não pode** ser recusado para **todos** — apenas para aquele prestador.

## Causa-raiz (o bug central)
A recusa por prestador é feita adicionando o `uid` do prestador ao array `rejectedBy` do pedido (sem mudar o `status`, para não cancelar para os demais). O design estava certo, mas **falhava em silêncio**:

- O app gravava **dois campos** na recusa: `rejectedBy` (arrayUnion) **e** `rejectedAt_<uid>` (timestamp por prestador) — em `RejectOrderReceiver.kt` e `ProviderOrdersFragment.kt`.
- A regra Firestore `validProviderRejectUpdate()` exigia `request.resource.data.diff(resource.data).affectedKeys().hasOnly(['rejectedBy'])`.
- Como a escrita também alterava `rejectedAt_<uid>`, o `hasOnly` **reprovava** → **toda recusa retornava PERMISSION_DENIED**, engolido pelo `catch`.
- Resultado: a recusa **nunca persistia**. O pedido continuava casando o prestador (não entrava em `rejectedBy`), e o campo `rejectedAt_<uid>` era **write-only** (nunca lido em app/painel/backend).

Segundo problema, no som: um alerta iniciado pelo **push FCM com o app fechado** (`FirebaseMessagingService`) chamava `NewOrderSoundHelper.startContinuousPlay`, mas **não** registrava o listener que para o som quando **outro** prestador aceita (esse listener — `setupOrderAcceptedListener` — só era criado pelo listener global em `ProviderNewOrderAlertManager.notifyNewOrders`). Logo, um som iniciado por FCM poderia tocar **para sempre** mesmo após o aceite de outro prestador.

## Correções aplicadas

### 1. Regra Firestore (`firestore.rules` + cópia `dashboard_admin/firestore.rules`) — **publicada**
`validProviderRejectUpdate()` foi endurecida e continua restrita a `rejectedBy`:

```
function validProviderRejectUpdate() {
  return isSignedIn()
    && resource.data.status in ['distributing', 'pending', 'available']
    && (resource.data.get('assignedProvider', null) == null
        || resource.data.get('assignedProvider', null) == '')
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['rejectedBy'])
    && request.auth.uid in request.resource.data.rejectedBy
    && request.resource.data.rejectedBy.hasAll(resource.data.get('rejectedBy', []));
}
```

Garantias:
- **Só `rejectedBy` muda** → `status`, preço e atribuição ficam intactos ⇒ **recusar nunca cancela o pedido para os outros**.
- Só em pedidos **não atribuídos** (`assignedProvider` vazio).
- O chamador **precisa** estar no novo `rejectedBy` (está adicionando a si mesmo) e **não pode remover** quem já recusou (`hasAll` do conjunto anterior).

Publicada via `firebase-tools deploy --only firestore:rules` (service account) — `released rules firestore.rules to cloud.firestore`. **Vale para o APK já instalado** (é correção de regra), mas o app precisa parar de mandar o campo extra (abaixo) para a recusa passar.

### 2. App — recusa grava só `rejectedBy`
- `RejectOrderReceiver.kt`: `update("rejectedBy", FieldValue.arrayUnion(uid))` (removido `rejectedAt_<uid>`, o `now`/import `Timestamp` órfãos).
- `ProviderOrdersFragment.kt`: idem.

### 3. App — som para sozinho no aceite mesmo se iniciado por FCM
- `ProviderNewOrderAlertManager.kt`: `setupOrderAcceptedListener()` agora monitora **todos** os `alertedOrderIds` (não só os novos); novo método público `watchAlertedOrders(orderIds)`.
- `FirebaseMessagingService.kt`: após `startContinuousPlay`, chama `ProviderNewOrderAlertManager.watchAlertedOrders(setOf(orderId))` para registrar o pedido e parar o som quando algum prestador aceitar.

## Comportamento final do som (confirmado no código)
- **Toca em loop** (`MediaPlayer.isLooping = true`) enquanto o pedido está disponível.
- **Para para o prestador X** quando ele recusa (`NewOrderSoundHelper.stopSound(orderId)`), e X passa a constar em `rejectedBy` → não é mais alertado.
- **Para para todos** quando **algum** prestador **aceita** (status → `assigned`) ou o pedido sai de disponível (cancelado/expirado).
- A recusa de X **não** afeta o som dos demais (status não muda).

## Verificação
- `./gradlew compileDebugKotlin` → EXIT=0; `./gradlew assembleDebug` → APK gerado.
- Prova no DEX do APK: string `rejectedAt` **ausente**; `rejectedBy` e `watchAlertedOrders` **presentes**.
- Regras: compilaram com sucesso (só warnings pré-existentes) e foram liberadas em produção.

## Arquivos tocados
- `firestore.rules`, `dashboard_admin/firestore.rules`
- `app/.../RejectOrderReceiver.kt`
- `app/.../ProviderOrdersFragment.kt`
- `app/.../ProviderNewOrderAlertManager.kt`
- `app/.../FirebaseMessagingService.kt`
