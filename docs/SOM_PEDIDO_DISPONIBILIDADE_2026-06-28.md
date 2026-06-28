# Alerta sonoro de novo pedido × disponibilidade do prestador — 2026-06-28

Ajuste para que o som de novo pedido **só toque se o prestador estiver
disponível**, continue tocando para todos os disponíveis até **recusa** (só para
ele) ou **aceite** (para todos), e toque **continuamente mesmo com o app fechado**.

## Fluxo de disponibilidade (atual)

- A disponibilidade é um botão na Home do prestador (`ProviderHomeActivity.toggleAvailability`)
  que grava `providers/{uid}.isAvailable` (boolean; default `true`) e chama
  `ProviderNewOrderAlertManager.refreshMonitoring()`.

## Onde o som é disparado (2 caminhos)

1. **App aberto** — `ProviderNewOrderAlertManager` escuta `orders` (status
   distributing/pending/available) e dispara `NewOrderSoundHelper.startContinuousPlay`.
2. **App fechado/background** — push FCM **data-only** do backend
   (`provider-notification.service.js`) → `FirebaseMessagingService.onMessageReceived`
   → inicia `AlertForegroundService` + som contínuo + `watchAlertedOrders`.

## O que estava errado

O `ProviderNewOrderAlertManager` checava aprovação do prestador mas **nunca
checava `isAvailable`** — então um prestador **indisponível** com o app aberto
**ainda ouvia** o alerta.

## Correções

1. **In-app gate de disponibilidade** (`ProviderNewOrderAlertManager.attachListenerIfProviderApproved`):
   lê `isAvailable` (providers, fallback users); se `false`, `stopMonitoring`
   (com `keepSound=false` → para qualquer som em andamento) e não atacha o listener.
   Ao voltar a ficar disponível, `toggleAvailability` chama `refreshMonitoring()` e
   o listener é reatachado.
2. **FCM re-check** (`FirebaseMessagingService`): antes de tocar o alerta de pedido,
   re-verifica `isAvailable` no cliente (`isProviderAvailableForOrders()`). Cobre a
   corrida de o prestador ficar indisponível entre o envio e a entrega do push.
   (O **backend já filtra** `isAvailable === false` — esta é uma 2ª barreira.)
3. **Re-armar o stop-no-aceite após refresh**: `stopMonitoring` sempre remove o
   `ordersStatusListener` (que para o som quando alguém aceita). Num refresh com
   `keepSound=true`, ele não era recriado → um aceite posterior não pararia o som.
   Agora, ao reatachar, se ainda há `alertedOrderIds`, o `setupOrderAcceptedListener`
   é re-armado.

## Comportamento garantido (já existente + reforçado)

- **Continua tocando** em loop nativo (`MediaPlayer.isLooping`) mantido vivo pelo
  `AlertForegroundService` — mesmo com o app fechado.
- **Recusa** (card "Rejeitar" ou ação da notificação via `RejectOrderReceiver`):
  grava só `rejectedBy` (arrayUnion do uid) → **não muda o status** (regra
  `validProviderRejectUpdate`), some só para ele; `stopSound(orderId)` para o som dele.
- **Aceite por outro**: status → `assigned`; `setupOrderAcceptedListener` (in-app e,
  no app fechado, armado por `watchAlertedOrders`) detecta e chama `stopSound` —
  para o som de **todos**.

## Escopo

- **App** (Kotlin) — exige **APK novo**.
- **Backend** — sem mudança (o filtro de `isAvailable` já existia).
- **Firebase/Vercel/Render** — sem mudança.
