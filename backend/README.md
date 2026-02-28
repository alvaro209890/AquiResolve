# Backend de Pagamentos

API REST dedicada ao fluxo de pagamentos do app Android, preparada para deploy no Render.

## Stack

- Node.js 20+
- Express
- Firebase Admin SDK
- Axios

## Endpoints

- `GET /api/health`
- `POST /api/payments/card`
- `POST /api/payments/pix`
- `GET /api/payments/:orderId/status`

Todos os endpoints de pagamento exigem:

- Header `Authorization: Bearer <firebase_id_token>`

## Variáveis de ambiente

Copie `backend/.env.example` para `.env` em desenvolvimento local e preencha:

- `PAGARME_SECRET_KEY`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_CLIENT_EMAIL`
- `FIREBASE_PRIVATE_KEY`
- `KEEP_ALIVE_ENABLED` (opcional, padrão `true` em produção)
- `KEEP_ALIVE_URL` (opcional, usa `RENDER_EXTERNAL_URL` quando disponível)
- `KEEP_ALIVE_INTERVAL_MS` (opcional, padrão `840000`)

Observação:

- `FIREBASE_PRIVATE_KEY` deve ser inserida como string única. O backend converte `\\n` em quebras de linha reais automaticamente.
- O keep-alive interno ajuda a reduzir períodos ociosos, mas se a plataforma suspender totalmente a instância, um ping externo agendado continua sendo a opção mais confiável.

## Execução local

```bash
cd backend
npm install
npm start
```

## Deploy no Render

1. Crie um novo Web Service.
2. Use o diretório raiz `backend`.
3. Configure:
   - Build Command: `npm install`
   - Start Command: `npm start`
   - Health Check Path: `/api/health`
4. Cadastre as variáveis de ambiente do `.env.example`.

Também há um blueprint em `backend/render.yaml`.
