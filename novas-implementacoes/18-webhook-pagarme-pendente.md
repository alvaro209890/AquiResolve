# 18 — Webhook Pagar.me (PENDENTE — aguarda ação do Álvaro)

**Data:** 2026-07-03
**Status:** Código pronto, configuração pendente
**Responsável pela configuração:** Álvaro (precisa acessar dashboard Pagar.me)

---

## Situação atual

O backend já tem o handler de webhook implementado e funcional:

- **Rota:** `POST /api/payments/webhook/pagarme` (sem autenticação Firebase — acesso externo)
- **Controller:** `backend/src/controllers/payments.controller.js` → `handlePagarmeWebhook()`
- **Sincronização:** `backend/src/services/payment-status-sync.service.js` → `syncPaymentStatusToFirestore()`

**Mas o webhook NUNCA foi ativado na Pagar.me** porque falta:

---

## O que o Álvaro precisa fazer

### 1. Pegar o Webhook Secret no dashboard da Pagar.me

- Acesse [dashboard.pagar.me](https://dashboard.pagar.me)
- Vá em **Configurações → Webhooks** (ou similar)
- Gere/visualize o **Webhook Secret** (string tipo `wh_sk_xxxxxxxxxxxx`)

### 2. Cadastrar a URL do webhook

No mesmo local do dashboard, cadastre a URL de destino:

```
https://aquiresolve.onrender.com/api/payments/webhook/pagarme
```

### 3. Entregar o secret pra IA configurar

Passe o secret pra IA colocar como env var `PAGARME_WEBHOOK_SECRET` no Render e implementar a validação HMAC-SHA256 (ver seção "O que a IA precisa implementar" abaixo).

---

## Por que isso é importante

**Sem webhook ativo**, o status de pagamento só é atualizado quando o app faz **polling** (`GET /:orderId/status`). Isso significa:

- Se o cliente fecha o app depois de gerar o PIX, **ninguém mais pergunta** se pagou
- O PIX pode ser compensado mas o pedido fica eternamente `awaiting_payment`
- A transição `awaiting_payment → distributing` nunca acontece se ninguém fizer polling

**Com webhook ativo**, a Pagar.me avisa o backend **no momento exato** da compensação, e o pedido vai pra `distributing` automaticamente.

---

## O que a IA precisa implementar (quando o Álvaro trouxer o secret)

Quando o Álvaro disser "tenho o webhook secret, configure", a IA deve:

1. **Adicionar `PAGARME_WEBHOOK_SECRET`** nas env vars do Render
2. **Adicionar `PAGARME_WEBHOOK_SECRET`** no `backend/src/config/env.js` (campo `pagarmeWebhookSecret`)
3. **Implementar validação HMAC-SHA256** no `verifyWebhookSecret()` — hoje só compara string estática, precisa calcular hash do raw body
4. **Adicionar idempotência** — verificar/salvar `event.id` pra não processar o mesmo webhook 2x
5. **Remover rate limit do webhook** — a rota não pode ter `paymentLimiter` (10 req/min pode bloquear a Pagar.me)
6. **Disparar notificação FCM** ao prestador quando o webhook confirmar pagamento (`status → distributing`)

---

## Env vars do Render (atuais — sem webhook)

```
GROQ_API_KEY
KEEP_ALIVE_INTERVAL_MS
KEEP_ALIVE_URL
KEEP_ALIVE_ENABLED
FIREBASE_PRIVATE_KEY
FIREBASE_CLIENT_EMAIL
FIREBASE_PROJECT_ID
CRON_SECRET
PAGARME_SECRET_KEY
PAGARME_BASE_URL
CORS_ORIGIN
NODE_ENV
```

Falta: `PAGARME_WEBHOOK_SECRET` ← Álvaro precisa pegar isso no dashboard Pagar.me

---

## Arquivos relevantes

| Arquivo | Função |
|---|---|
| `backend/src/routes/payments.routes.js:15` | Rota `POST /webhook/pagarme` |
| `backend/src/controllers/payments.controller.js:236-314` | `verifyWebhookSecret()` + `handlePagarmeWebhook()` |
| `backend/src/services/payment-status-sync.service.js` | `syncPaymentStatusToFirestore()` — atualiza Firestore |
| `backend/src/config/env.js` | `loadEnv()` — NÃO tem `pagarmeWebhookSecret` |
