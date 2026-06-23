# 09 — Runbook: Ativar a IA (Groq) dos planos 6 e 8

**Prioridade:** 🟢 Operacional · **Pré-requisito:** planos `06` e `08` (código já no `main`, commit `4d93917`)

---

## 🎯 Objetivo

A IA do **app** (plano 06 — Assistente que identifica o nicho) e do **painel** (plano 08 — Copiloto
do Manual) já estão **100% codadas**. Falta apenas **ligar a chave da Groq** e fazer deploy. Este
runbook é o passo a passo para isso — e para testar.

> **Regra de ouro:** a chave Groq vive **só nos servidores** (Render e Vercel). **Nunca** vai no APK
> nem em `NEXT_PUBLIC_*` (qualquer var `NEXT_PUBLIC_` é exposta ao browser).

---

## 0. Obter a chave Groq

1. Acesse <https://console.groq.com> → **API Keys** → **Create API Key**.
2. Copie a chave (formato `gsk_...`). Ela só aparece uma vez.
3. (Opcional) Anote o id do modelo vigente em **Models** — o default do código é
   `llama-3.3-70b-versatile`. Se mudar, configure via `GROQ_MODEL` (sem alterar código).

---

## 1. Plano 06 — Assistente do app (backend no Render)

O app chama `POST /api/ai/classify` no backend; o backend guarda a chave e fala com a Groq.

### 1.1 Configurar a variável no Render
Use a skill **`aquiresolve-render`** (ou o painel do Render → serviço **AquiResolve** →
**Environment**). Adicione:

```
GROQ_API_KEY=gsk_...                  # obrigatória
GROQ_MODEL=llama-3.3-70b-versatile    # opcional (default já é este)
```

### 1.2 Deploy manual do backend
O backend tem **autoDeploy OFF** — push no GitHub **não** redeploya. Dispare o deploy via a skill
`aquiresolve-render` (API Render) **ou** "Manual Deploy" no painel. O código novo
(`/api/ai/classify`) só entra em produção após esse deploy.

### 1.3 Testar via curl
Precisa de um **ID token Firebase** de um usuário logado (a rota exige `Authorization: Bearer`).
Pegue um token de teste e:

```bash
curl -X POST https://aquiresolve.onrender.com/api/ai/classify \
  -H "Authorization: Bearer <ID_TOKEN_FIREBASE>" \
  -H "Content-Type: application/json" \
  -d '{"description":"minha pia está vazando na cozinha","niches":["Elétrica","Encanador","Limpeza","Ar condicionado"]}'
```

Esperado:
```json
{ "ok": true, "niche": "Encanador", "confidence": 0.9, "message": "Parece um problema hidráulico. Posso te levar para Encanador?" }
```

Casos a validar:
- "a luz da sala não acende" → `Encanador`? não → **`Elétrica`**.
- "preciso de uma faxina" → **`Limpeza`**.
- "asdkjhasd" (sem sentido) → `niche: null` + mensagem amigável.
- **Sem `GROQ_API_KEY`** → HTTP 503 `AI_NOT_CONFIGURED` (o app cai no fallback, não trava).

### 1.4 Testar no app
Gere o APK (`./gradlew assembleDebug`), abra a Home → card **"🤖 Não sabe o nome do serviço?"**
(rodapé da Home) **ou** digite na busca algo sem resultado → toque no CTA. Descreva um problema →
deve sugerir o nicho com **"Sim, continuar"** levando ao `CreateOrderActivity` no nicho certo.

---

## 2. Plano 08 — Copiloto do painel (Vercel)

A aba **Manual** (`/dashboard/manual`) tem um chat no topo que chama `POST /api/assistant`
(rota Next.js no servidor).

### 2.1 Configurar a variável na Vercel
Use a skill **`aquiresolve-vercel`** (ou o painel da Vercel → projeto
`aquiresolve-dashboard` → **Settings → Environment Variables → Production**):

```
GROQ_API_KEY=gsk_...                  # obrigatória (server-only, NÃO usar NEXT_PUBLIC_)
GROQ_MODEL=llama-3.3-70b-versatile    # opcional
```

> Atualize também `infra-config/vercel/vercel.env` (cópia local, não versionada) para manter o
> espelho da configuração.

### 2.2 Deploy do painel
Sem auto-deploy do GitHub — publique pela CLI:

```bash
cd dashboard_admin
npx vercel deploy --prod --yes
```

### 2.3 Testar no painel
Abra **Manual do Painel** → use os chips de exemplo ou pergunte:
- "Como cadastro um parceiro?" → deve citar **Configurações → Parceiros AquiResolve → Novo parceiro**.
- "Como reembolso um pedido?" → deve citar **Gestão de Pedidos / Financeiro**.
- "Como crio um banner?" → deve citar **Configurações → Banners da Home**.
- Pergunta fora do escopo → resposta educada, sem inventar telas.

Confira no **DevTools → Network** que **`GROQ_API_KEY` nunca aparece** em nenhum bundle/resposta do
cliente (a chamada vai para `/api/assistant`, que roda no servidor).

---

## 3. Checklist de aceite final

- [ ] `GROQ_API_KEY` no Render + deploy do backend → `curl /api/ai/classify` retorna nicho válido.
- [ ] APK novo: Assistente da Home sugere nicho e leva ao pedido; fallback funciona sem chave.
- [ ] `GROQ_API_KEY` na Vercel + deploy do painel → Copiloto responde em passos citando telas reais.
- [ ] Chave **não** vaza no browser (Network) nem no APK (sem `GROQ_*` em `BuildConfig`).
- [ ] Sem chave: app e painel seguem usáveis (degradação graciosa, não quebra).

---

## ⚠️ Gotchas

- **Id do modelo muda:** se a Groq aposentar `llama-3.3-70b-versatile`, troque por `GROQ_MODEL`
  (env), sem mexer no código nem no APK.
- **autoDeploy off (Render):** lembrar do deploy manual — a rota nova não sobe sozinha com o push.
- **Custo/abuso:** o backend já tem rate-limit (`aiLimiter`, 15/min/IP) + exige ID token Firebase.
  O painel é uso interno (poucos admins) e limita histórico/tamanho da resposta.
- **`response_format: json_object` (app):** o backend faz parse defensivo; JSON inválido vira
  `niche: null` + mensagem amigável (nunca derruba o app).
