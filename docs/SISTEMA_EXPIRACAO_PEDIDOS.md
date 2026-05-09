# SISTEMA DE EXPIRAÇÃO DE PEDIDOS "UNACCEPTED" (expired)

## Visão Geral

Quando um cliente cria um pedido e ele fica em distribuição (`distributing`/`pending`) por **1h30 sem nenhum prestador aceitar**, o sistema:

1. **Muda o status** do pedido para `expired`
2. **Envia notificação push** pro cliente avisando
3. **O pedido para de aparecer** pros prestadores
4. **Continua visível** pro cliente (com status `expired`)

## Arquitetura

```
[cron-job.org a cada 10min]
        |
        ▼
[Backend Node.js] → POST /api/cron/expire-orders
        |
        ▼
[Firebase Admin SDK]
        ├── Firestore: orders/{id}.status = "expired"
        ├── Firestore: notifications/{id} (notificação salva)
        └── FCM Push Notification → Cliente Android
```

## Como funciona

### Backend (Node.js)

**Arquivo:** `backend/src/services/order-expiration.service.js`

- Função `expireOrders()`:
  1. Busca pedidos com status `distributing` ou `pending`
  2. Verifica se `distributionStartedAt` (ou `createdAt`) tem mais de 90 minutos
  3. Usa `batch` do Firestore pra atualizar em lote
  4. Salva notificação na coleção `notifications`
  5. Envia push FCM via `admin.messaging().send()`

**Rota:** `POST /api/cron/expire-orders`
- Protegida por header `x-cron-secret`
- Retorna `{ ok: true, checked, expired, errors, timestamp }`

### App Android

**Já funciona sem alterações porque:**
- Prestadores buscam pedidos filtrando por `distributing`/`pending` → `expired` não aparece
- `OrderData.STATUS_EXPIRED = "expired"` já existe no companion object
- Cliente vê todos os pedidos (incluindo `expired`) via `getUserOrders()`

## Configuração do Cron

### Opção 1: cron-job.org (recomendado)

1. Criar conta em [cron-job.org](https://cron-job.org)
2. Criar job:
   - **URL:** `https://aquiresolve.onrender.com/api/cron/expire-orders`
   - **Method:** POST
   - **Headers:** `x-cron-secret: <CRON_SECRET>`
   - **Schedule:** Every 10 minutes
   - **Content-Type:** application/json
   - **Request Body:** `{"secret": "<CRON_SECRET>"}`

### Opção 2: Render Cron Jobs

Se o plano Render suportar, adicionar no `render.yaml`:

```yaml
cronJobs:
  - schedule: "*/10 * * * *"
    name: expire-orders
    command: "curl -X POST https://aquiresolve.onrender.com/api/cron/expire-orders -H 'x-cron-secret: <CRON_SECRET>'"
```

## Variáveis de Ambiente

| Variável | Obrigatória | Descrição |
|---|---|---|
| `FIREBASE_PROJECT_ID` | ✅ | ID do projeto Firebase |
| `FIREBASE_CLIENT_EMAIL` | ✅ | Email da conta de serviço |
| `FIREBASE_PRIVATE_KEY` | ✅ | Chave privada da conta de serviço |
| `CRON_SECRET` | ✅ | Senha pra proteger o endpoint do cron |

> **Nota:** As variáveis `FIREBASE_*` já são usadas pelo backend de pagamentos. Só precisa adicionar `CRON_SECRET` no Render.

## Testando Localmente

```bash
# 1. Rodar o backend
cd backend
cp .env.example .env  # preencher FIREBASE_* e CRON_SECRET
npm install
npm start

# 2. Simular o cron
curl -X POST http://localhost:3000/api/cron/expire-orders \
  -H 'Content-Type: application/json' \
  -d '{"secret": "sua-chave-aqui"}'
```
