# Novas Implementações — AquiResolve (Home Premium + IA)

> **MENSAGEM DO GESTOR / DIRETRIZ PRINCIPAL:**
> "O objetivo agora é criar uma **Home Premium** com: Banner rotativo, Cashback em destaque, Categorias horizontais, Combos promocionais, Parceiros AquiResolve, Pedidos recentes, Busca inteligente, e Estrutura preparada para IA.
> **Observação importante:** invista primeiro em organização visual e experiência do usuário antes da IA. A maior oportunidade hoje é fazer o cliente encontrar e contratar um serviço em menos de 30 segundos. Isso gera conversão imediatamente. 🚀💙🧡"

> **INSTRUÇÃO PARA A PRÓXIMA IA DESENVOLVEDORA:**
> Você está assumindo a base de código atual do app cliente (Android/Kotlin nativo). Embora haja recomendações externas para uso de Flutter, **o desenvolvimento deve continuar na base nativa atual (`app/src/main/java...`)**.

**Stack confirmada:** implementação **no app Kotlin existente** (`app/`), reaproveitando
toda a base já pronta (Firebase, catálogo dinâmico, pagamentos, cashback, chat).

---

## ✅ Estado atual (2026-06-22) — leva concluída em código

Os **8 planos** estão com **código pronto e validado** (app compila: `compileDebugKotlin` +
`processDebugResources`; backend monta com as rotas; painel passa `tsc --noEmit`). Commit `4d93917`.

| Plano | O que falta |
|-------|-------------|
| 1–5 (Banner, Categorias, Combos, Parceiros, Busca) | Apenas QA visual no device. |
| **7 — Home Premium** | Montada (ordem Premium + pull-to-refresh + CTA IA). Só QA no device. |
| **6 — Assistente IA (app)** | **Configurar `GROQ_API_KEY` no Render** + deploy do backend + teste. |
| **8 — Copiloto IA (painel)** | **Configurar `GROQ_API_KEY` na Vercel** + deploy do painel + teste. |

> 👉 **Para ligar a IA (planos 6 e 8), siga o passo a passo em
> [`09-ativacao-ia-runbook.md`](./09-ativacao-ia-runbook.md).** Sem a chave Groq, o app e o painel
> continuam funcionando normalmente — a IA apenas exibe um fallback amigável ("ver todos os serviços").

---

## 📁 Índice dos planos

| # | Plano | O que entrega | Prioridade |
|---|-------|---------------|------------|
| — | [`00-roadmap-e-prioridades.md`](./00-roadmap-e-prioridades.md) | Fases, ordem de execução, dependências, métricas de sucesso | — |
| 1 | [`01-banner-rotativo.md`](./01-banner-rotativo.md) | Carrossel rotativo no topo da Home (cashback, promoções, combos, parceiros) | 🟢 Alta |
| 2 | [`02-categorias-horizontais.md`](./02-categorias-horizontais.md) | Categorias (nichos) em scroll horizontal com ícone/emoji | 🟢 Alta |
| 3 | [`03-combos-promocionais.md`](./03-combos-promocionais.md) | Seção "🔥 Combos Promocionais" (foto, descrição, valor, economia) | 🟡 Média |
| 4 | [`04-parceiros-aquiresolve.md`](./04-parceiros-aquiresolve.md) | Seção "Parceiros AquiResolve" (descontos, cashback, cupons) | 🟡 Média |
| 5 | [`05-busca-inteligente.md`](./05-busca-inteligente.md) | Sugestões instantâneas ao digitar (serviços/nichos) — atalho de contratação | 🟢 Alta |
| 6 | [`06-assistente-ia-groq.md`](./06-assistente-ia-groq.md) | 🤖 Assistente do **app cliente**: descreve o problema → IA identifica o nicho e direciona | 🔵 Baixa (último) |
| 7 | [`07-home-premium-montagem.md`](./07-home-premium-montagem.md) | Montagem final: reorganização da Home unindo todas as seções | 🟢 (fecha a leva) |
| 8 | [`08-assistente-ia-painel-admin.md`](./08-assistente-ia-painel-admin.md) | 🤖 Copiloto do **painel admin** (Groq, dentro da aba Manual): admin pergunta "como faço X?" → passos com onde clicar | 🟡 Média |
| 9 | [`09-ativacao-ia-runbook.md`](./09-ativacao-ia-runbook.md) | 🔑 **Runbook**: como obter a chave Groq, configurar no Render/Vercel, fazer deploy e testar a IA dos planos 6 e 8 | 🟢 Operacional |

> **Nota:** os planos `01–07` evoluem o **app cliente**; o plano `08` é uma trilha **do painel admin**
> (web), independente da Home do app. O plano `09` é o **runbook operacional** para ativar a IA.

---

## 🧭 Como ler cada plano

Todos os planos seguem o **mesmo template**:

1. **Objetivo** — o porquê em uma frase.
2. **Contexto atual** — arquivos reais do repo que serão tocados ou reaproveitados.
3. **Escopo** — o que entra (✅) e o que **não** entra (⛔) nesta entrega.
4. **Modelo de dados** — coleção Firestore + regras (sempre `read: isSignedIn()` / `write: false` → Admin SDK).
5. **Painel admin** — onde o conteúdo é gerenciado (Next.js + API route Admin SDK).
6. **App (camadas)** — model → repository → adapter → layout → Activity/seção da Home.
7. **Design/UX** — paleta, espaçamentos, estados (loading/vazio/erro).
8. **Checklist** — itens marcáveis, separados por App / Painel / Firestore / QA.
9. **Critérios de aceite** — quando considerar "pronto".
10. **Riscos & gotchas** — armadilhas conhecidas do projeto.
11. **Estimativa** — esforço relativo.

---

## 🎯 Princípios desta leva

- **Reaproveitar antes de criar.** Combos já têm lógica de desconto em `PromotionManager` +
  `app_config/cashback`; categorias já vêm de `CatalogRepository`/`catalog_services`. Não duplicar.
- **Conteúdo gerenciável pelo painel.** Banner, combos e parceiros são **dados** (Firestore),
  nunca hardcode — o admin edita sem novo APK (mesmo padrão de `catalog_services`).
- **Segurança das regras.** Toda coleção nova: leitura para autenticado, escrita só via Admin SDK
  (rotas `/api/...` do painel). Nunca `allow write: if true`.
- **Fallback sempre.** Se a coleção estiver vazia/offline, a Home não pode quebrar — esconde a
  seção ou usa fallback estático (igual `CatalogRepository`).
- **Sem segredo no APK.** A chave da IA (Groq) **não** vai no app; passa por proxy no backend
  Render (mesmo padrão do proxy `/api/route` do mini-mapa).
- **ViewBinding.** O app já usa `viewBinding true`; toda tela nova usa binding, sem `findViewById`
  solto (exceto inflar itens dinâmicos, como já é feito em `ClientHomeActivity`).

---

## 🗂 Convenções de status

Marque o progresso editando os checklists de cada plano:

- `[ ]` não iniciado · `[~]` em andamento · `[x]` concluído

Status global da leva: ver tabela em [`00-roadmap-e-prioridades.md`](./00-roadmap-e-prioridades.md).

---

## ⚠️ Antes de começar a codar (lembrete do projeto)

- **Novos serviços/itens na lista do app só aparecem após novo APK** — mudanças de *dados*
  (preço, banner, combo) refletem na hora; mudanças de *código* exigem `./gradlew assembleDebug`.
- **Deploy do painel é manual** (`npx vercel deploy --prod --yes` em `dashboard_admin/`).
- **Regras Firestore** publicam com `firebase deploy --only firestore:rules --project aplicativoservico-143c2`
  (ver skill `aquiresolve-firebase`).
- **Commit direto no `main`** (sem PR), conforme convenção do repo.
</content>
</invoke>
