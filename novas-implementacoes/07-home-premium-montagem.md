# 07 — Home Premium (Montagem Final)

**Prioridade:** 🟢 (fecha a leva) · **Fase:** 4 · **Complexidade:** Média

---

## ✅ Implementado (2026-06-22, commit `4d93917`)

Home montada na ordem Premium e **compilando** (`compileDebugKotlin` + `processDebugResources`).
Falta apenas QA visual no device.

- **Ordem final** (`activity_client_home.xml`): Busca → Banner → Saudação (1 linha, com o nome) →
  Categorias → **Cashback** → Combos → Parceiros → Pedidos recentes → **CTA Assistente IA** (`cardAssistant`).
- **Botão "Ver Serviços" removido** (redundante com categorias + bottom nav).
- **Pull-to-refresh:** `NestedScrollView` (`contentScroll`) dentro de `SwipeRefreshLayout`
  (`swipeRefresh`); `setupSwipeRefresh()` re-chama os `setup*`/`load*`.
- Cada seção dinâmica segue isolada (erro/vazio → `GONE`); insets preservados em `setupWindowInsets`.

---

## 🎯 Objetivo

Integrar todas as seções (banner, categorias, combos, parceiros, busca, IA) numa **Home Premium**
coesa, com ordem que maximiza conversão, scroll/insets corretos e estados tratados — sem regressão
do que já funciona (cashback, pedidos recentes, bottom nav).

> **DIRETRIZ UX PREMIUM:** O cliente deve ser capaz de bater o olho, encontrar a categoria/combo desejado e contratar um serviço em **menos de 30 segundos**. O layout deve ser moderno, organizado, claro, sem bagunça visual. Essa é a maior prioridade técnica agora, superando a implementação imediata de Inteligência Artificial.

---

## 🧩 Contexto atual

- `ClientHomeActivity.kt` + `activity_client_home.xml`:
  - AppBar (botões: Central/chat, notificações, carrinho, perfil) + barra de busca (`etSearch`).
  - Conteúdo (`NestedScrollView` > `LinearLayout` vertical): boas-vindas → botão "Ver Serviços" →
    **card de cashback** (`cardCashback`, já existe) → **pedidos recentes** (`loadRecentOrders`, já existe).
  - `BottomNavigationView` (home/orders/services/profile).
  - Insets tratados em `setupWindowInsets()`; badge da Central em `startCentralBadgeListener()`.
- ViewBinding (`ActivityClientHomeBinding`).

---

## 🧱 Nova ordem da Home (proposta Premium)

Do topo para baixo, dentro do `LinearLayout` do conteúdo:

```text
┌─ AppBar (mantém) ──────────────────────────────┐
│  [Central] [Notif] [Carrinho] [Perfil]         │
│  🔍 Busca inteligente (etSearch + sugestões)   │  ← plano 05 (Contratação em 1 toque)
└────────────────────────────────────────────────┘
1. 🎞  Banner rotativo                              ← plano 01 (Destaques: Cashback, Promos)
2. 👋  Saudação curta (1 linha, enxugar a atual)
3. 🧭  Categorias horizontais                       ← plano 02 (Substitui lista gigante, ex: ⚡ Elétrica, 🚿 Hidráulica)
4. 💸  Card de cashback em destaque                 ← MANTÉM, já existe (Destaque visual forte)
5. 🔥  Combos promocionais                          ← plano 03 (Ex: Combo Casa Nova, Limpeza Completa)
6. 🤝  Parceiros AquiResolve                        ← plano 04 (Ex: Leroy Merlin, com descontos/cupons)
7. 🧾  Pedidos recentes                             ← MANTÉM, já existe (Facilita recompra)
8. 🤖  Card "Assistente AquiResolve" (CTA IA)       ← plano 06 (Estrutura preparada, IA entra depois)
└─ BottomNavigation (mantém) ────────────────────┘
```

> **Racional de Conversão:** O que gera contratação rápida vem primeiro (busca + banner + categorias). Cashback logo após reforça valor. Combos e parceiros monetizam. Pedidos recentes fecham o fluxo de retenção. O Assistente IA é a alternativa para indecisos e fica mais para o final como conveniência (deixe estruturalmente pronto para quando a IA for ligada).

---

## ✅ Escopo

**Entra:**
- ✅ Inserir/ordenar todas as seções no `activity_client_home.xml`.
- ✅ Cada seção some sozinha quando vazia (não deixar buracos).
- ✅ Carregamentos paralelos/independentes (uma seção lenta não trava as outras).
- ✅ Revisar insets, scroll, padding e bottom nav após adicionar tudo.
- ✅ Pull-to-refresh (opcional, mas recomendado) para recarregar conteúdo dinâmico.
- ✅ Instrumentação Analytics consolidada.

**Não entra (⛔):**
- ⛔ Redesign da AppBar/bottom nav (mantém).
- ⛔ Mudar fluxo de pedido/carrinho/pagamento.

---

## 📱 Implementação

### Layout
- Adicionar os containers de cada seção na ordem acima, **cada um envolto** num container com `id`
  para alternar `visibility` (GONE quando vazio).
- Enxugar a seção de boas-vindas atual (hoje ocupa muito espaço: título 24sp + subtítulo + botão
  "Ver Serviços" + legenda). Com categorias/banner, o botão "Ver Serviços" vira redundante →
  avaliar remover ou reduzir.

### Activity (`ClientHomeActivity`)
- Centralizar a orquestração em `onResume`/`onCreate`:
  - `setupBannerCarousel()` (01)
  - `setupCategories()` (02)
  - `setupCombos()` (03)
  - `setupPartners()` (04)
  - busca já no `setupClickListeners` + listener (05)
  - botão assistente (06)
  - `loadRecentOrders()` / `loadCashbackBalance()` (já existem)
- Cada `setup*` roda no `lifecycleScope`, trata erro isolado e esconde a própria seção em caso de vazio/falha.
- Garantir catálogo (`CatalogRepository` + `CatalogServiceRepository`) carregado cedo
  (idealmente pré-aquecido no `AppApplication`).

### Estados visuais
- **Loading:** skeleton/placeholder leve por seção (ou simplesmente seção oculta até carregar).
- **Vazio:** seção `GONE`.
- **Erro:** seção `GONE` + log; nunca toast agressivo na Home.

### Pull-to-refresh (opcional)
- O app já tem `androidx.swiperefreshlayout`. Envolver o `NestedScrollView` num `SwipeRefreshLayout`
  e, no refresh, rechamar os `setup*` + `load*`.

---

## 🎨 Design / UX (Padrão Premium)

- **Sensação de Modernidade:** Evitar listas pesadas. Usar os *scrolls* horizontais.
- **Espaçamento:** Espaçamento vertical consistente entre seções (~24dp) para dar "respiro".
- **Títulos de seção:** 20sp bold, cor `text_primary`, com emoji onde fizer sentido (ex: 🔥 Combos, 🤝 Parceiros).
- **Paleta:** `primary_color` `#FF7A00`, `secondary_color` `#10B981`, fundos neutros.
- **Cashback em Destaque:** Garantir que o card de cashback existente seja visualmente atraente.
- **Insets:** Garantir que o último item não fique escondido atrás do bottom nav (padding inferior já é
  calculado em `setupWindowInsets` — revalidar com o novo conteúdo).

---

## ✔️ Checklist

### App
- [x] Reordenar/inserir todas as seções no `activity_client_home.xml` (cada uma em container com id).
      Ordem Premium aplicada: Busca → Banner → Saudação → Categorias → **Cashback** → Combos → Parceiros → Pedidos recentes → **CTA Assistente IA**.
- [x] Enxugar seção de boas-vindas / reavaliar botão "Ver Serviços". Saudação virou 1 linha
      (personalizada com o nome) e o botão "Ver Serviços" foi **removido** (redundante com categorias/bottom nav).
- [x] Orquestrar `setup*`/`load*` em `ClientHomeActivity` (cada um isolado e tolerante a falha).
- [x] Esconder seções vazias (visibility GONE) — já era o comportamento de cada `apply*`.
- [x] Pré-aquecer catálogo no `AppApplication` (já feito; `setup*` também garante via `load()`).
- [x] `SwipeRefreshLayout` com refresh de tudo (`setupSwipeRefresh()` re-chama os `setup*`/`load*`).
- [~] Revisar insets/scroll/padding com todo o conteúdo (insets preservados em `setupWindowInsets`; falta QA no device).
- [x] Consolidar eventos Analytics (cada seção já loga seu evento; CTA IA loga `ia_assistente_open`).

### QA (regressão + novo)
- [ ] Cashback e pedidos recentes continuam funcionando.
- [ ] Bottom nav, AppBar, badge da Central, busca: tudo intacto.
- [ ] Cada seção some quando vazia; Home nunca mostra buraco/erro.
- [ ] Scroll suave; nada cortado sob AppBar/bottom nav (testar telas pequenas e grandes).
- [ ] Offline: Home abre, seções dinâmicas escondem/fallback, sem crash.
- [ ] Performance: abrir a Home não trava (carregamentos paralelos, sem bloquear UI thread).
- [ ] Dark/light e fontes grandes (acessibilidade) não quebram o layout.

---

## 🟢 Critérios de aceite

1. Home reúne todas as seções na ordem definida, coesa e sem regressão.
2. Qualquer seção vazia desaparece — zero buracos/erros visíveis.
3. Da abertura ao início de um pedido leva poucos toques (meta < 30s).

---

## ⚠️ Riscos & gotchas

- **NestedScrollView + vários RecyclerViews:** usar alturas adequadas (carrossel/categorias com
  altura fixa; listas horizontais com `wrap_content`); evitar listas verticais aninhadas roláveis.
- **Insets:** o padding inferior é calculado dinamicamente (`dpToPx(96) + systemBars.bottom`);
  revalidar após adicionar seções para o último card não colar no bottom nav.
- **Listeners/cancelamento:** seguir o padrão atual (`onPause` remove listeners) para banner
  auto-scroll, badges e quaisquer snapshots — evitar vazamento.
- **Carregamento sequencial trava a Home:** disparar os `setup*` independentes (não `await` em série
  bloqueante); cada seção aparece quando estiver pronta.

---

## 📦 Estimativa

Média — ~1,5 a 2 dias (integração + QA de regressão). Depende das seções `01`–`06` existirem
(pode iniciar com placeholders).
</content>
