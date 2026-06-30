# Reembolso com solicitação do cliente + aprovação/recusa no painel — 2026-06-29

## Pedido

Melhorar o estorno: o **cliente** deve **descrever o motivo** e **anexar fotos**; a
solicitação é **aprovada ou recusada no painel admin**; se o admin **recusar**, informa o
**motivo**, que **volta ao cliente no app, dentro do pedido**.

## Fluxo novo (ponta a ponta)

```
App (cliente)            Firestore (orders)              Painel (admin)
─────────────            ──────────────────              ──────────────
Solicitar reembolso
 motivo + fotos   ──▶  refundStatus = 'requested'  ──▶  fila "Reembolsos"
                       refundReason, refundPhotos        (motivo + galeria de fotos)
                                                              │
                          ┌───────────────────────────────────┴───────────────┐
                     Aprovar e estornar                                    Recusar (motivo)
                          │                                                     │
              POST /api/orders/[id]/refund                    POST /api/orders/[id]/refund/reject
              estorna na Pagar.me                             refundStatus = 'rejected'
              refundStatus = 'completed'                      refundRejectionReason = <motivo>
                          │                                                     │
                          └──────────────► FCM + notifications ◄────────────────┘
                                                  │
App (cliente) ◄── no pedido: "Reembolso concluído"  /  "❌ Reembolso recusado. Motivo: …"
```

## Estados de `refundStatus`
`requested` (solicitado pelo cliente, em análise) → `completed` (aprovado e estornado) **ou**
`rejected` (recusado, com `refundRejectionReason`). `pending`/`processing`/`partial`/`failed`
permanecem para os fluxos legados/automáticos.

## App (`app/`)

- **Modelo** (`models/OrderData.kt`): novos campos `refundReason`, `refundPhotos: List<String>`,
  `refundRejectionReason`.
- **`FirebaseOrderManager.requestRefund(orderId, reason, photoUrls)`**: valida (dono do
  pedido, pedido pago, motivo ≥ 10 chars, sem solicitação ativa) e grava
  `refundStatus='requested'` + motivo + fotos.
- **`RefundRequestActivity`** (novo) + `activity_refund_request.xml` + registro no Manifest:
  o cliente escreve o motivo e anexa até 5 fotos. As fotos sobem para Storage
  `order_images/{orderId}/refund_*.jpg` (regra já existente) via `FirebaseImageManager`;
  depois chama `requestRefund`.
- **`OrderDetailsActivity`**:
  - botão **"Solicitar reembolso"** (no card de cancelamento) para o cliente, em pedido
    pago, sem solicitação ativa; após uma recusa vira "Solicitar reembolso novamente";
  - exibe os estados na seção de reembolso, incluindo **`rejected` com o motivo do admin**
    ("❌ Reembolso recusado. Motivo: …") e `requested` ("📨 Em análise").

## Painel (`dashboard_admin/`)

- **`GET /api/orders/refunds/pending`**: passou a listar `refundStatus in ['requested','pending']`
  e a devolver `refundReason` e `refundPhotos`.
- **`POST /api/orders/[id]/refund/reject`** (novo, Admin SDK, `operarFinanceiro`): grava
  `refundStatus='rejected'` + `refundRejectionReason` + `refundReviewedAt/By`, **notifica o
  cliente** (FCM + `notifications`) e registra `adminLogs` (`reject_refund`).
- **Página `Financeiro → Reembolsos`**: cada cartão mostra o **motivo do cliente** e a
  **galeria de fotos** (abre em nova aba) e tem dois botões:
  - **Aprovar e estornar** → `POST /api/orders/[id]/refund` (Pagar.me) — comportamento já existente;
  - **Recusar** → diálogo com motivo obrigatório → endpoint de recusa.
- **Aprovação** reaproveita o endpoint de estorno existente (sem duplicar lógica Pagar.me).

## Regras Firebase

- **Firestore (`firestore.rules`, publicada)**: nova ramificação em `validClientOrderUpdate`
  permitindo o cliente **abrir** a solicitação — `refundStatus == 'requested'` num pedido
  **pago**, mexendo só em `refundStatus/refundReason/refundPhotos/refundRequestedAt/updatedAt`.
  A **decisão** (aprovar/recusar) é exclusiva do Admin SDK (rotas do painel).
- **Storage**: sem mudança — as fotos usam `order_images/{orderId}/...`, que já tem
  `read: isSignedIn()` / `write: signedInCanModifyImage`.

## Deploys

- Firestore rules → `firebase deploy --only firestore:rules` (released).
- Painel → `vercel deploy --prod` (READY, aliased a `aquiresolve-dashboard.vercel.app`).
- App → novo APK debug.

## Arquivos

**App**: `models/OrderData.kt`, `FirebaseOrderManager.kt`, `RefundRequestActivity.kt` (novo),
`res/layout/activity_refund_request.xml` (novo), `res/layout/activity_order_details.xml`,
`OrderDetailsActivity.kt`, `AndroidManifest.xml`.
**Painel**: `app/api/orders/refunds/pending/route.ts`, `app/api/orders/[id]/refund/reject/route.ts` (novo),
`app/dashboard/financeiro/reembolsos/page.tsx`.
**Regras**: `firestore.rules`.

## Verificação
- App: `:app:assembleDebug` → BUILD SUCCESSFUL.
- Painel: `next build` → compilado; rotas `/api/orders/[id]/refund/reject`,
  `/api/orders/refunds/pending` e `/dashboard/financeiro/reembolsos` geradas.

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug). Exige novo APK nos aparelhos.
