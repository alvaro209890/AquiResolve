# exclusao-conta

Site Next.js para solicitação de exclusão de conta (LGPD), com wizard em 4 etapas e API `POST /api/account/delete` (Firebase Admin).

## Desenvolvimento local

```bash
pnpm install
cp .env.example .env.local   # se existir; configure Firebase
pnpm dev
```

Variáveis: `NEXT_PUBLIC_FIREBASE_*` e `FIREBASE_SERVICE_ACCOUNT` (JSON em uma linha).

## Logo

Marca exibida a partir de `public/aq.jpg`.
