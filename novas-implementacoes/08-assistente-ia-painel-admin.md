# 08 — Assistente IA do Painel Admin (Groq, dentro do Manual)

**Prioridade:** 🟡 Média · **Fase:** 3 (trilha do **painel**, paralela à IA do app `06`) · **Complexidade:** Média · **Status:** 🟢 Código pronto (falta chave + teste)

---

## ✅ Implementado (2026-06-22, commit `4d93917`)

Código **completo e passando no `tsc --noEmit`**. Falta só configurar `GROQ_API_KEY` na Vercel +
deploy + teste — ver runbook [`09-ativacao-ia-runbook.md`](./09-ativacao-ia-runbook.md).

| Camada | Arquivo |
|--------|---------|
| Conteúdo único | `dashboard_admin/lib/manual-content.ts` (`SECTIONS/CONCEPTS/INFRA` + `manualAsPromptContext()`) |
| Rota IA | `dashboard_admin/app/api/assistant/route.ts` (`POST`; `runtime='nodejs'`; chave só no servidor) |
| Widget | `dashboard_admin/components/manual/assistant-chat.tsx` (chat + exemplos + estados) |
| Integração | `app/dashboard/manual/page.tsx` consome o lib e renderiza `<AssistantChat />` no topo |

> A página do Manual **deixou de declarar o conteúdo inline** — agora importa de `lib/manual-content.ts`.
> Ao adicionar uma área nova ao painel, edite **esse** arquivo: Manual e Copiloto acompanham juntos.

> **Diferente do plano `06`** (que é a IA do **app cliente**, identifica nicho a partir do problema).
> Aqui a IA é para o **administrador**: dentro da aba **Manual do Painel** (`/dashboard/manual`),
> o admin **pergunta "como faço X no painel?"** e a IA responde **passo a passo, dizendo onde clicar**
> (qual menu, qual botão, qual campo). É um "copiloto de uso do painel", ancorado no próprio
> conteúdo do Manual — não inventa telas que não existem.

---

## 🎯 Objetivo

Reduzir a curva de aprendizado do painel: qualquer admin (mesmo novo) digita uma dúvida em
linguagem natural e recebe **instruções clicáveis** ("Vá em *Configurações → Parceiros AquiResolve*,
clique em *Novo parceiro*, preencha…"). A IA usa **Groq** (rápido e barato) e é **fundamentada**
no conteúdo já existente da aba Manual (as seções documentadas), garantindo respostas fiéis a
**este** painel.

---

## 🧩 Contexto atual (já existe base!)

- **Aba Manual** já criada: `dashboard_admin/app/dashboard/manual/page.tsx` — documenta cada área do
  painel (Painel, Serviços, Controle, Usuários, Pedidos, Financeiro, Relatórios, Configurações,
  Área Master) + conceitos + infraestrutura, em arrays `SECTIONS`/`CONCEPTS`/`INFRA`.
- **Groq** já é usado no ecossistema (chave fica fora do cliente — ver `06` e memória do projeto).
  No painel (Next.js), a chave pode viver como **env de servidor** (`GROQ_API_KEY` na Vercel),
  nunca exposta ao browser (igual `FIREBASE_SERVICE_ACCOUNT`).
- **Padrão de API route** Admin/servidor: `dashboard_admin/app/api/*/route.ts` (Node, server-side).
- **UI kit** pronto: `components/ui/*` (Card, Button, Input/Textarea, etc.) + `useToast`.

> ⚠️ **Fundamentação (grounding) é o ponto-chave.** A IA deve responder **a partir do conteúdo do
> Manual** (passado como contexto no prompt), não do conhecimento geral — assim ela cita os nomes
> reais de menus/botões e não alucina fluxos inexistentes.

---

## ✅ Escopo

**Entra:**
- ✅ Widget de chat **dentro da aba Manual** (topo da página): campo de pergunta + respostas.
- ✅ Rota de servidor `dashboard_admin/app/api/assistant/route.ts` que chama a Groq com:
  - **system prompt** "você é o copiloto do painel AquiResolve; responda em passos numerados,
    citando o caminho exato de menus/botões; se não souber, diga para procurar no Manual".
  - **contexto** = o conteúdo do Manual (seções) serializado.
  - a **pergunta** do admin (+ histórico curto da conversa).
- ✅ Resposta em **passos numerados** ("1. Abra…, 2. Clique…, 3. Preencha…").
- ✅ Estados: carregando, erro (Groq fora → mensagem amigável), vazio.
- ✅ Chave Groq **só no servidor** (`GROQ_API_KEY`).

**Não entra (⛔):**
- ⛔ Executar ações pelo admin (a IA **orienta**, não clica/salva por ele).
- ⛔ Acesso a dados sensíveis de clientes/pedidos no prompt (só documentação de uso).
- ⛔ RAG com banco vetorial — o Manual é pequeno; cabe inteiro no contexto (sem embeddings agora).
- ⛔ Treino/fine-tuning de modelo.

---

## 🏗 Arquitetura

```
Aba Manual (page.tsx)
  └─ <AssistantChat />  ──POST /api/assistant──►  Groq Chat Completions
                                                   (GROQ_API_KEY no servidor)
        ▲                                                │
        └──────────── resposta (passos) ◄───────────────┘
   contexto do prompt = conteúdo do Manual (SECTIONS/CONCEPTS/INFRA)
```

- **Fonte de verdade única do conteúdo:** extrair `SECTIONS/CONCEPTS/INFRA` da `page.tsx` para um
  módulo compartilhado (ex.: `lib/manual-content.ts`). A página **renderiza** esse conteúdo e a
  rota `/api/assistant` o **injeta no prompt** — assim Manual e IA nunca divergem.
- **Modelo Groq:** usar o modelo de chat atual da Groq (ex.: família Llama 3.x "versatile/instant").
  Conferir o nome vigente na Groq ao implementar (modelos mudam) e deixar configurável por env
  (`GROQ_MODEL`, com default).
- **Por que rota Next.js (e não proxy no Render):** o painel já é Next.js com servidor próprio;
  uma API route mantém a chave fora do browser sem depender do backend. (Alternativa válida:
  reaproveitar um proxy no Render, como no `06`, se quiser centralizar a chave Groq num lugar só.)

---

## 🗄 Modelo de dados (opcional)

Sem coleção nova obrigatória. **Opcional**, para melhorar respostas com o tempo:

```jsonc
// assistant_logs/{autoId}  — telemetria de uso (opcional)
{
  "question": "como cadastro um combo?",
  "answer": "1. Vá em Serviços → Combos Promocionais …",
  "adminUid": "…",
  "createdAt": <serverTimestamp>
}
```
Regra: `read/write: if false` (só Admin SDK grava pela rota). Útil para ver dúvidas frequentes e
enriquecer o Manual. **Não** logar dados sensíveis.

---

## 🖥 Painel admin (camadas)

### 1. Conteúdo compartilhado
`dashboard_admin/lib/manual-content.ts` — exporta `SECTIONS`, `CONCEPTS`, `INFRA` (movidos de
`page.tsx`) + um helper `manualAsPromptContext(): string` que serializa tudo em texto enxuto.

### 2. Rota de IA
`dashboard_admin/app/api/assistant/route.ts` (POST):
- Body: `{ question: string, history?: {role,content}[] }`.
- Monta `messages`: system (instruções + `manualAsPromptContext()`) → history → user.
- Chama Groq (`https://api.groq.com/openai/v1/chat/completions`, `Authorization: Bearer GROQ_API_KEY`).
- Retorna `{ success, answer }`. Trata erro/timeout com `{ success:false, error }`.

### 3. Widget de chat
`dashboard_admin/components/manual/assistant-chat.tsx` (client component):
- Card no topo da aba Manual: textarea "Pergunte como fazer algo no painel…", botão Enviar.
- Lista de mensagens (pergunta do admin + resposta em passos, render simples de markdown/linhas).
- Histórico curto na memória do componente; estados loading/erro; exemplos clicáveis
  ("Como cadastro um parceiro?", "Como reembolso um pedido?", "Como crio um banner?").

### 4. Integração na aba Manual
`page.tsx`: importar o conteúdo de `lib/manual-content.ts` (deixar de declarar inline) e renderizar
`<AssistantChat />` acima do índice. O resto da documentação permanece igual (texto navegável).

---

## 🎨 Design / UX

- Bloco de destaque no topo do Manual: ícone 🤖 + título "Pergunte ao Copiloto do Painel".
- Resposta sempre em **passos numerados**; caminhos de menu em **negrito** ou `código`.
- Chips de perguntas-exemplo para o admin começar com um clique.
- Tom objetivo, em pt-BR; se a dúvida for fora do painel, orientar a procurar no Manual/suporte.

---

## ✔️ Checklist

### Infra / Variáveis
- [ ] `GROQ_API_KEY` (e opcional `GROQ_MODEL`) nas Environment Variables da Vercel (server-only). **← pendente: aguardando a chave.**
- [ ] Atualizar `infra-config/vercel/vercel.env` + `vercel-config.md` com as novas variáveis.

### Painel
- [x] `lib/manual-content.ts` (movido `SECTIONS/CONCEPTS/INFRA` + `manualAsPromptContext()`).
- [x] `app/api/assistant/route.ts` (POST → Groq; chave só no servidor `runtime='nodejs'`; tratamento de erro/timeout).
- [x] `components/manual/assistant-chat.tsx` (chat + passos + exemplos clicáveis + estados loading/erro).
- [x] `app/dashboard/manual/page.tsx` usa o conteúdo compartilhado e renderiza `<AssistantChat />` no topo.
- [ ] (Opcional) coleção `assistant_logs` + gravação na rota — **não implementado** (fora do escopo mínimo).

### QA
- [ ] "Como cadastro um combo?" → passos citando *Serviços → Combos Promocionais → Novo combo*. **← teste com a chave.**
- [ ] "Como reembolso um pedido?" → passos citando *Gestão de Pedidos* / Financeiro.
- [ ] "Como crio um banner?" → passos citando *Configurações → Banners da Home*.
- [ ] Pergunta fora de escopo → resposta educada, sem alucinar telas.
- [ ] Groq indisponível → mensagem de erro amigável (não quebra a aba). **(já tratado na rota/o widget mostra o erro)**
- [ ] `GROQ_API_KEY` **não** aparece em nenhum bundle do cliente (checar no DevTools/Network).

---

## 🟢 Critérios de aceite

1. O admin abre a aba **Manual**, pergunta como fazer algo e recebe **passos com onde clicar**.
2. As respostas batem com os nomes **reais** de menus/botões do painel (porque são fundamentadas
   no conteúdo do Manual).
3. A chave Groq nunca chega ao navegador (fica na rota de servidor).

---

## ⚠️ Riscos & gotchas

- **Alucinação** se a IA não for fundamentada: SEMPRE injetar o conteúdo do Manual no prompt e
  instruir "responda só a partir deste conteúdo; se não houver, diga que não há no Manual".
- **Conteúdo desatualizado:** se a IA e o Manual lerem fontes diferentes, divergem. Por isso o
  conteúdo é **um módulo só** (`lib/manual-content.ts`), consumido pelos dois.
- **Nome do modelo Groq muda** com o tempo — deixar em env (`GROQ_MODEL`) e conferir o vigente.
- **Custo/limite:** limitar tamanho da resposta e o histórico enviado; é uso interno (poucos admins).
- **Segurança:** não colocar dados de clientes/pedidos no contexto — só documentação de uso do painel.

---

## 📦 Estimativa

Média — ~0,5 dia (rota + grounding) + ~0,5 dia (widget de chat + integração) + ajustes de prompt.
