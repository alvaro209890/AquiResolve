# Fila de reembolsos no painel + foto de prestador separada — 2026-06-29

Duas frentes nesta entrega:

1. **Painel admin** — fila dedicada de "Reembolsos pendentes" (antes o admin não tinha
   onde ver os pedidos que aguardam estorno).
2. **App mobile** — a conta de **prestador** passa a ter foto própria, separada da conta
   de **cliente**, e o cliente volta a ver a foto do prestador no pedido.

---

## 1. Painel — Reembolsos pendentes

### Problema
O estorno era 100% reativo: o cancelamento de pedido pago (cliente) e a expiração
(backend) apenas marcavam `orders.refundStatus = 'pending'`, mas **não havia tela** que
listasse essa fila. O admin só conseguia estornar caçando o pedido em Faturamento. Quem
não soubesse do pedido, nunca estornava — o cliente ficava pago e sem atendimento.

### Solução
- **Endpoint** `GET /api/orders/refunds/pending`
  (`dashboard_admin/app/api/orders/refunds/pending/route.ts`, Admin SDK, permissão
  `financeiro`): lista os pedidos com `refundStatus == 'pending'`, ordenados pela data da
  solicitação (em memória — sem índice composto). Devolve cliente, serviço, valor
  (`finalPrice` ou `estimatedPrice`), motivo, status e se há transação Pagar.me.
- **Página** `Financeiro → Reembolsos`
  (`dashboard_admin/app/dashboard/financeiro/reembolsos/page.tsx`): KPIs (quantidade e
  valor total pendente), busca, cartão por pedido e botão **"Processar reembolso"** com
  diálogo de confirmação. A ação chama o endpoint já existente
  **`POST /api/orders/[id]/refund`** (estorna na Pagar.me, marca `refunded`/`completed`,
  notifica o cliente e registra `adminLogs`).
- **Permissões**: ver a fila exige `financeiro`; **processar** exige `operarFinanceiro`
  (o botão é desabilitado sem ela e o endpoint reforça no servidor). Pedidos sem
  transação Pagar.me aparecem sinalizados e com o botão bloqueado (estorno manual).
- **Sidebar**: novo item "Reembolsos" no grupo Financeiro
  (`components/layout/sidebar.tsx`). `PATH_PERMISSIONS` já cobre `/dashboard/financeiro/*`
  com `financeiro` — sem mudança.
- **Manual do painel** (`lib/manual-content.ts`): nova seção explicando a fila (o
  Copiloto do painel passa a saber responder sobre ela).

### Importante
A fila **não** torna o estorno automático — continua sendo um admin que confirma. Ela só
garante visibilidade para nenhuma pendência se perder. (Tornar automático ao
cancelar/expirar é um passo possível adiante.)

---

## 2. App — foto de prestador separada da de cliente

### Problema (causa raiz)
Um mesmo usuário pode ter conta de **cliente** e de **prestador**. As duas telas de foto
subiam para o **mesmo arquivo** no Storage — `profile_images/{uid}/profile_{uid}.jpg` — e o
`ProviderProfileFragment` ainda gravava a mesma URL em `users/{uid}` **e** `providers/{uid}`
("mesma foto para ambos os perfis"). Logo:
- era impossível ter foto de prestador diferente da de cliente (mesmo arquivo);
- se o usuário só tinha setado a foto como **cliente** (tela `ProfileActivity`, que grava
  só em `users/`), `providers/{uid}.profileImageUrl` ficava vazio e **o cliente não via a
  foto do prestador** no pedido.

### Solução
- **`ProviderProfileFragment`**:
  - upload agora vai para arquivo dedicado **`provider_{uid}.jpg`** (separado do
    `profile_{uid}.jpg` do cliente);
  - `updateProfileImageInFirestore` grava **somente** em `providers/{uid}.profileImageUrl`
    (via `FirebaseProviderManager.updateProfileImage`) — **não toca mais em `users/`**. A
    foto de prestador fica independente da de cliente.
- **`ProfileActivity`** (cliente): inalterada — continua gravando só em
  `users/{uid}.profileImageUrl` (arquivo `profile_{uid}.jpg`).
- **Cliente vê o prestador** — `OrderDetailsActivity.loadProviderImage` agora lê
  `providers/{uid}.profileImageUrl` e, se vazio, cai para `users/{uid}.profileImageUrl`.
  Assim o cliente sempre vê uma foto (inclusive de prestadores legados que só tinham foto
  de usuário). O **chat** (`openChat`) e a **lista** (`OrdersTabFragment`) já tinham esse
  fallback.
- **Prestador vê o cliente** — já entregue antes (card direcional + `loadClientImage`).

### Regras Firebase
Nenhuma mudança necessária nesta entrega:
- `storage.rules`: `profile_images` já está com `read: isSignedIn()` (ajustado na entrega
  anterior) — a contraparte consegue ler a foto do outro.
- `firestore.rules`: `users` e `providers` já são `read: isSignedIn()`.

---

## Arquivos

**Painel**
- `dashboard_admin/app/api/orders/refunds/pending/route.ts` (novo)
- `dashboard_admin/app/dashboard/financeiro/reembolsos/page.tsx` (novo)
- `dashboard_admin/components/layout/sidebar.tsx`
- `dashboard_admin/lib/manual-content.ts`

**App**
- `app/src/main/java/com/aquiresolve/app/ProviderProfileFragment.kt`
- `app/src/main/java/com/aquiresolve/app/OrderDetailsActivity.kt`

## Verificação
- App: `:app:assembleDebug` → BUILD SUCCESSFUL.
- Painel: `next build` → compilado com sucesso (rotas `/dashboard/financeiro/reembolsos`
  e `/api/orders/refunds/pending` geradas). Erros de `tsc` restantes são pré-existentes
  (recharts) e ignorados por `ignoreBuildErrors`.

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug). Painel exige deploy na Vercel.
