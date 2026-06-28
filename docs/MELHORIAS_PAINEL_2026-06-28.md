# Melhorias do Painel Admin — 2026-06-28

Sessão de melhoria do **painel administrativo** (`dashboard_admin/`): correção de
bug de layout, modernização visual da navegação, ampliação do Manual e Copiloto
IA mais inteligente. Build de produção validado (`next build` = sucesso).

---

## 1. Bug corrigido — barra lateral duplicada

**Sintoma:** em algumas telas (ex.: **Serviços → Checklists de OS**), ao abrir a
barra lateral de seleção de aba ela **duplicava**.

**Causa raiz:** essas páginas já vivem dentro de `app/dashboard/layout.tsx`, que
renderiza `<Sidebar>` + `<Header>`. Mas elas **também** se envolviam em
`<AppShell>` (que renderiza outra Sidebar + Header completos) → **dois shells
empilhados**. No mobile, abrir o drawer mostrava a sidebar duplicada.

**Correção:** removido o `<AppShell>` das páginas que já estão sob o layout do
dashboard (o shell vem do layout). Páginas corrigidas:
- `app/dashboard/servicos/checklists/page.tsx`
- `app/dashboard/financeiro/fechamento/page.tsx`
- `app/dashboard/financeiro/folha-pagamento/page.tsx`
- `app/dashboard/financeiro/movimento-caixa/page.tsx`

> **Regra para o futuro:** páginas dentro de `app/dashboard/**` **não** devem usar
> `AppShell` — o layout já provê Sidebar + Header. `AppShell` é só para rotas
> fora do grupo `/dashboard` (ex.: `/servicos/*`, `/orders` legados).

---

## 2. Front mais moderno e sugestivo

`components/layout/sidebar.tsx`:
- **Item ativo** (topo e subitem) ganhou **gradiente** `primary → amber` + sombra
  suave, em vez do preenchimento chapado — fica claro onde você está.
- **Grupo com filho ativo** destaca o pai (texto `primary` + leve fundo `primary/10`),
  então dá para ver o contexto mesmo com o grupo recolhido.
- **Subitem ativo** ganhou uma **barrinha de acento** à esquerda; itens inativos
  têm micro-deslize no hover (`translate-x`), deixando a navegação mais viva.

(Mudanças puramente de classes Tailwind — sem alterar a estrutura/IDs, baixo risco.)

---

## 3. Manual do Painel ampliado

`lib/manual-content.ts` ganhou as áreas que faltavam (também melhora o grounding
da IA — o Copiloto é fundamentado neste arquivo):
- **Serviços → Checklists de OS** — consulta de fotos, materiais, GPS, assinaturas
  e finalização por código do cliente.
- **Configurações → Banners do Prestador** — carrossel "Aumente seus ganhos" da
  Home do prestador (coleção `provider_banners`), separado dos banners do cliente.

---

## 4. Copiloto IA do painel mais inteligente

`app/api/assistant/route.ts` — system prompt reforçado:
- Termina respostas com **"📍 Veja também:"** sugerindo 1–2 telas relacionadas
  (sempre reais, do Manual).
- Diz **explicitamente** quando a ação reflete no app **sem novo APK** (banners,
  combos, parceiros, preços, cashback) e quando **exige** novo APK.
- Diante de um problema relatado ("não aparece", "deu erro"), sugere a **causa mais
  provável** segundo o Manual (permissão faltando, item inativo, deploy pendente)
  antes dos passos.
- Tópicos dominados atualizados (Banners do Prestador, Checklists de OS).

A chave `GROQ_API_KEY` continua **só no servidor** (Vercel). Modelo configurável
por `GROQ_MODEL` (default `llama-3.3-70b-versatile`).

---

## Publicação

| Plataforma | Mudou nesta sessão? | Ação |
|---|---|---|
| **GitHub** (Delta `main` + alvaro) | Sim (painel) | push |
| **Vercel** (painel admin) | Sim | `npx vercel deploy --prod --yes` |
| **Firebase** (regras/índices) | Não | já publicado na sessão anterior (aceite/recusa) |
| **Render** (backend pagamentos) | Não | já no ar (data-only FCM da sessão anterior) |

Validação: `next build` concluiu com sucesso (todas as rotas). `compileDebugKotlin`
do app continua verde (sessão anterior).
