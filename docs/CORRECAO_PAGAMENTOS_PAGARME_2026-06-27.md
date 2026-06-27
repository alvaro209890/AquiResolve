# Correcao pagamentos Pagar.me - 2026-06-27

Este documento registra a correcao feita no backend de pagamentos depois de falhas no app
debug ao tentar pagar servicos via PIX ou cartao.

## Resumo executivo

O app Android estava chegando ao backend autenticado corretamente, mas a Pagar.me recusava
o payload com HTTP 422 (`The request is invalid.`). O problema estava no contrato final
enviado pelo backend para `POST /orders`, nao em Firestore rules nem em Firebase Auth.

Correcao publicada:

- Commit: `642e229 Fix Pagar.me payment payload normalization`
- Backend Render: servico `srv-d6hmk2p4tr6s73bu5fm0` (`AquiResolve`)
- Deploy: manual via Render API, com backend `live`
- Health check validado: `https://aquiresolve.onrender.com/api/health`

## Causas encontradas

### 1. Cartao com campos legados do APK

O APK debug envia os dados do cartao em formato historico:

- `card_number`
- `card_holder_name`
- `card_expiration_date` (`MMYY`)
- `card_cvv`
- `credit_card.billing_address`

A Pagar.me v5 espera o formato dentro de `credit_card.card`:

- `number`
- `holder_name`
- `exp_month`
- `exp_year`
- `cvv`
- `billing_address`

Sem normalizacao no backend, a Pagar.me respondia 422 com mensagens como:

```txt
payments[0].credit_card.card: The card number is required
payments[0].credit_card.card: The card expiration date is invalid.
```

### 2. Codigo de checkout de carrinho longo demais para o gateway

O checkout de carrinho usa codigo local no formato:

```txt
cart_checkout_<uid>_<timestamp>
```

Esse identificador deve continuar existindo para o backend sincronizar todos os pedidos
do carrinho por `cartCheckoutCode`, mas nao precisa ir inteiro como `order.code` ou
`items[0].code` para a Pagar.me.

Agora o backend gera um codigo curto e deterministico para o gateway (`aqr_<sha256>`),
mantendo o codigo local completo em:

```json
{
  "metadata": {
    "order_id": "cart_checkout_<uid>_<timestamp>",
    "payment_source": "prepared_cart_checkout"
  }
}
```

### 3. Endereco do cliente incompleto no payload final

O backend agora monta `customer.address` a partir do pedido ou do primeiro item do
carrinho (`address`, `zipCode`, `city`, `state`). Isso reduz rejeicoes do gateway e
evita depender do APK para todos os campos de cobranca.

## Arquivos alterados

- `backend/src/services/payment-authorization.service.js`
  - normaliza codigo enviado ao gateway;
  - normaliza dados de cartao legados para Pagar.me v5;
  - normaliza dados PIX (`additional_information`);
  - monta `customer.address`;
  - preserva `metadata.order_id` como fonte local para sincronizacao.
- `backend/src/services/payment-mapper.service.js`
  - extrai `errors` quando a Pagar.me devolve objeto por campo;
  - devolve detalhes sanitizados em `error.details`, sem secrets nem numero completo de cartao.

## Evidencias de validacao

Validacoes locais:

```bash
node --check backend/src/services/payment-authorization.service.js
node --check backend/src/services/payment-mapper.service.js
```

Smoke de payload sem chamar o gateway:

- Pedido individual: `items[0].code` preservado quando curto, endereco preenchido.
- Carrinho: codigo local com 56 caracteres vira codigo gateway com 28 caracteres, mas
  `metadata.order_id` permanece igual ao codigo local completo.

Smoke real contra Pagar.me antes do deploy:

- PIX: `POST /orders` passou a responder HTTP 200, mas a transacao voltou `failed` com
  `action_forbidden`.
- Cartao: `POST /orders` passou a responder HTTP 200; cartao de teste em chave live voltou
  `not_authorized`, que e recusa normal do emissor/gateway, nao erro de payload.

Smoke autenticado no backend publicado:

- Login Firebase REST com usuario de teste do app gerou ID token.
- `POST /api/payments/pricing/calculate` respondeu OK.
- `POST /api/payments/pix` respondeu HTTP 200 do backend e criou ordem na Pagar.me.
- `POST /api/payments/card` respondeu HTTP 200 do backend e criou ordem na Pagar.me.

## Estado atual do PIX

O payload do PIX ja e aceito pela Pagar.me, mas a conta/chave configurada no Render ainda
recebe do gateway:

```txt
action_forbidden |  | Erro no gateway
```

Comparacao de chaves feita sem imprimir valores:

- Chave `PAGARME_SECRET_KEY` do Render: autentica e cria ordem, mas PIX volta
  `action_forbidden`.
- Chave privada local do painel/Vercel (`API_KEY_PRIVATE_PAGARME`): recebe HTTP 403
  `Authorization has been denied for this request` quando usada diretamente na Core API.

Conclusao: a falha restante de PIX e configuracao/permissao da conta Pagar.me usada pelo
Render, nao contrato do app/backend. Para concluir o PIX ponta a ponta, liberar PIX na
conta Pagar.me ou trocar `PAGARME_SECRET_KEY` do Render por uma chave valida com PIX
habilitado, entao redeployar/retestar.

## Como retestar sem cobrar valor alto

Use sempre pedido temporario de baixo valor e remova os documentos depois.

Checklist:

1. Criar pedido temporario em `orders/{id}` com:
   - `clientId` do usuario de teste;
   - `status = awaiting_payment`;
   - `paymentStatus = awaiting_payment`;
   - `estimatedPrice = 1`;
   - endereco completo.
2. Obter ID token Firebase do cliente de teste.
3. Chamar `POST https://aquiresolve.onrender.com/api/payments/pix` ou `/card`.
4. Verificar:
   - backend responde HTTP 200;
   - logs Render mostram `Resposta do createOrder recebida da Pagar.me`;
   - Firestore `payment_sessions/{gatewayOrderId}` foi criado;
   - remover o pedido temporario e a sessao de pagamento do teste.

## Comandos uteis

Health do backend:

```bash
curl -s https://aquiresolve.onrender.com/api/health
```

Deploy manual Render depois de push para `alvaro/main`:

```bash
RKEY=$(grep '^RENDER_API_KEY=' .render-credentials | cut -d= -f2)
SRV=srv-d6hmk2p4tr6s73bu5fm0
curl -s -X POST \
  -H "Authorization: Bearer $RKEY" \
  -H "Content-Type: application/json" \
  "https://api.render.com/v1/services/$SRV/deploys" \
  -d '{"clearCache":"do_not_clear"}'
```

Listar logs recentes de pagamentos no Render:

```bash
RKEY=$(grep '^RENDER_API_KEY=' .render-credentials | cut -d= -f2)
OWNER=tea-d6hlq0l6ubrc73buaqr0
SRV=srv-d6hmk2p4tr6s73bu5fm0
START=$(date -u -d '15 minutes ago' +%Y-%m-%dT%H:%M:%SZ)
END=$(date -u +%Y-%m-%dT%H:%M:%SZ)
curl -sG -H "Authorization: Bearer $RKEY" "https://api.render.com/v1/logs" \
  --data-urlencode "ownerId=$OWNER" \
  --data-urlencode "resource=$SRV" \
  --data-urlencode "startTime=$START" \
  --data-urlencode "endTime=$END" \
  --data-urlencode "direction=backward" \
  --data-urlencode "limit=80"
```

## Regras de manutencao

- O APK pode continuar enviando payload legado; o backend deve ser a camada de compatibilidade
  com a Pagar.me.
- Nao confiar em `amount` vindo do APK; o backend continua recalculando pelo Firestore.
- Nao remover `metadata.order_id`; ele e usado para mapear o pagamento ao pedido local ou ao
  `cartCheckoutCode`.
- Nao colocar chave Pagar.me no APK.
- Nao imprimir secrets em logs, docs ou respostas.
