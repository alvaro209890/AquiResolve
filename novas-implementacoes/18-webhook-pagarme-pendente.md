# 18 — Webhook Pagar.me (código PRONTO — falta só configuração do Álvaro)

**Data:** 2026-07-03 · **Atualizado:** 2026-07-04 (parte de código implementada)
**Status:** ✅ Código implementado e testado · ⏳ Configuração na Pagar.me/Render pendente
**Responsável pela configuração:** Álvaro (precisa acessar dashboard Pagar.me)

---

## O que já está implementado (2026-07-04)

Todos os itens de código do plano original foram feitos:

### 1. Validação de autenticidade (HMAC + alternativas)
- **`backend/src/utils/webhook-auth.js`** (novo, lógica pura) — `verifyWebhookRequest()` aceita **qualquer um** dos mecanismos, sempre com comparação constant-time (`crypto.timingSafeEqual`):
  - **HMAC do raw body** (sha256 ou sha1) nos headers `x-hub-signature-256`, `x-hub-signature`, `x-pagarme-signature`, `x-signature` — com ou sem prefixo `sha256=`/`sha1=`;
  - **Basic auth** (`Authorization: Basic`) — bate se a senha, o usuário ou o par `user:pass` for igual ao secret;
  - **Bearer** (`Authorization: Bearer <secret>`);
  - **secret estático** em header (`x-pagarme-webhook-secret` etc.) ou query `?secret=` (compatibilidade com o formato antigo).
- Suporte a múltiplos mecanismos é proposital: a Pagar.me v5 configura a autenticação do webhook de formas diferentes conforme a conta (Basic no cadastro do endpoint vs. assinatura) — assim funciona seja qual for o modo que o dashboard oferecer.
- **Raw body:** `app.js` captura os bytes originais (`express.json({ verify })` → `req.rawBody`) — a assinatura HMAC é calculada sobre eles, não sobre o JSON re-serializado.
- **Sem secret configurado**, o comportamento continua o legado: aceita com warning no log (não quebra nada antes da configuração).

### 2. Idempotência por event.id
- **`backend/src/services/webhook-event.service.js`** (novo) — antes de processar, o handler faz um **claim atômico** criando `payment_webhook_events/{eventId}` com `create()` (falha com ALREADY_EXISTS se outra entrega já criou).
  - Duplicata/retry → responde `200 { ok: true, duplicate: true }` sem reprocessar.
  - Sucesso → doc marcado `status: 'processed'` com o resultado.
  - Erro no processamento → claim é **liberado** (doc apagado) para o retry da Pagar.me funcionar.
  - **Fail-open:** se o Firestore estiver fora, processa mesmo assim (o handler reconsulta o status real no gateway, então processar 2x é inofensivo; nunca processar é o cenário ruim).
- O id do evento vem de `payload.id` **somente quando difere do id do pedido** no gateway (payloads sem id de evento distinto não são deduplicáveis com segurança e processam direto).

### 3. Webhook fora do rate limit
- Rota movida para **`backend/src/routes/payments-webhook.routes.js`**, montada em `app.js` **antes** do `paymentLimiter` (`app.use('/api/payments/webhook', ...)`). A URL externa **não mudou**: `POST /api/payments/webhook/pagarme`.
- As demais rotas de `/api/payments` continuam com o limite de 10 req/min por IP.

### 4. Notificação FCM aos prestadores — já era coberto (sem código novo)
- O `provider-notification.service.js` (iniciado no `server.js`) escuta `orders` com `status in [pending, distributing, ...]` via `onSnapshot`. Quando o webhook vira `awaiting_payment → distributing`, o pedido **entra** nessa query (docChange `added`) e o FCM data-only dispara para os prestadores aprovados do nicho — exatamente o mesmo caminho do fluxo por polling. Nada a fazer.

### Config
- `backend/src/config/env.js` → novo campo `pagarmeWebhookSecret` (lê `PAGARME_WEBHOOK_SECRET`, fallback `PAYMENT_WEBHOOK_SECRET`).
- `backend/.env.example` documenta a variável.

### Testes
- **`backend/test/webhook-auth.test.js`** (novo): 16 testes de autenticação/idempotência (HMAC válido/adulterado/secret errado, Basic, Bearer, estático, query, tamanhos diferentes, extração do event id). Suite completa: `npm test` no `backend/` → **26/26 pass** (Node 20).
- **Smoke E2E local** (app real em porta efêmera): sem credencial → 401; assinatura errada → 401; assinatura válida → passa da autenticação; 15 req seguidas no webhook → **zero 429**; rota comum de payments → 429 na 11ª (limiter intacto).

---

## O que o Álvaro precisa fazer (único bloqueio restante)

### 1. Cadastrar o webhook no dashboard da Pagar.me
- Acesse [dashboard.pagar.me](https://dashboard.pagar.me) → **Configurações → Webhooks** (ou similar)
- Cadastre a URL de destino:

```
https://aquiresolve.onrender.com/api/payments/webhook/pagarme
```

- Selecione os eventos de **pedido/cobrança** (no mínimo: `order.paid`, `order.payment_failed`, `order.canceled`, `charge.paid`, `charge.payment_failed`, `charge.refunded` — se a UI listar por grupo, marque os grupos "order" e "charge").

### 2. Pegar o segredo de autenticação
- No mesmo cadastro, configure/copiate a autenticação que o dashboard oferecer — **qualquer formato serve** (o backend aceita todos):
  - se oferecer **Basic auth** (usuário/senha): anote os dois;
  - se oferecer **secret/assinatura** (string tipo `wh_sk_...`): anote a string.

### 3. Trazer o segredo pra IA
- Diga "tenho o secret do webhook, configure" e passe o valor. A IA vai:
  1. Setar `PAGARME_WEBHOOK_SECRET` no Render (skill `aquiresolve-render`) — p/ Basic auth, usar o formato `usuario:senha`;
  2. Disparar o deploy manual do backend (autoDeploy é OFF);
  3. Validar ao vivo (log do Render deve mostrar "Webhook Pagar.me autenticado").

> **Enquanto isso não acontece**, nada quebra: o backend segue aceitando webhook sem validação (com warning) e o app segue confirmando pagamento por polling. Mas o cenário "cliente gera PIX e fecha o app" só se resolve com o webhook ativo.

---

## Por que isso é importante

**Sem webhook ativo**, o status de pagamento só é atualizado quando o app faz **polling** (`GET /:orderId/status`):
- Se o cliente fecha o app depois de gerar o PIX, **ninguém mais pergunta** se pagou;
- O PIX pode ser compensado mas o pedido fica eternamente `awaiting_payment`;
- A transição `awaiting_payment → distributing` nunca acontece sem polling.

**Com webhook ativo**, a Pagar.me avisa o backend **no momento da compensação**, o pedido vai pra `distributing` e o FCM de novo pedido dispara para os prestadores do nicho automaticamente.

---

## Arquivos relevantes

| Arquivo | Função |
|---|---|
| `backend/src/routes/payments-webhook.routes.js` | Rota `POST /api/payments/webhook/pagarme` (fora do rate limit) |
| `backend/src/utils/webhook-auth.js` | Validação de autenticidade (HMAC/Basic/Bearer/estático, constant-time) — pura, testada |
| `backend/src/services/webhook-event.service.js` | Idempotência (`payment_webhook_events/{eventId}`, claim atômico) |
| `backend/src/controllers/payments.controller.js` | `verifyWebhookSecret()` + `handlePagarmeWebhook()` |
| `backend/src/services/payment-status-sync.service.js` | `syncPaymentStatusToFirestore()` — atualiza Firestore |
| `backend/src/services/provider-notification.service.js` | Listener que dispara o FCM quando o pedido vira `distributing` |
| `backend/src/config/env.js` | `pagarmeWebhookSecret` (`PAGARME_WEBHOOK_SECRET`) |
| `backend/test/webhook-auth.test.js` | 16 testes (`npm test`) |
