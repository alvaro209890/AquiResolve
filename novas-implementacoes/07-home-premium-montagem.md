# 07 — Home Premium (Montagem Final)

**Prioridade:** 🟢 (fecha a leva) · **Fase:** 4 · **Complexidade:** Média

---

## 🎯 Objetivo

Integrar todas as seções (banner, categorias, combos, parceiros, busca, IA) numa **Home Premium**
coesa, com ordem que maximiza conversão, scroll/insets corretos e estados tratados — sem regressão
do que já funciona (cashback, pedidos recentes, bottom nav).

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

## 🧱 Nova ordem da Home (proposta)

Do topo para baixo, dentro do `LinearLayout` do conteúdo:

```
┌─ AppBar (mantém) ──────────────────────────────┐
│  [Central] [Notif] [Carrinho] [Perfil]         │
│  🔍 Busca inteligente (etSearch + sugestões)   │  ← plano 05
└────────────────────────────────────────────────┘
1. 🎞  Banner rotativo                              ← plano 01
2. 👋  Saudação curta (1 linha, enxugar a atual)
3. 🧭  Categorias horizontais                       ← plano 02
4. 💸  Card de cashback (MANTÉM, já existe)
5. 🔥  Combos promocionais                          ← plano 03
6. 🤝  Parceiros AquiResolve                        ← plano 04
7. 🤖  Card "Assistente AquiResolve" (CTA)          ← plano 06
8. 🧾  Pedidos recentes (MANTÉM, já existe)
└─ BottomNavigation (mantém) ────────────────────┘
```

> Racional: o que gera contratação rápida vem primeiro (busca + banner + categorias). Cashback logo
> após reforça valor. Combos/parceiros monetizam. Assistente é alternativa para indecisos. Pedidos
> recentes fecham (retenção/recompra).

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

## 🎨 Design / UX

- Espaçamento vertical consistente entre seções (~24dp).
- Títulos de seção no mesmo estilo (20sp bold, `text_primary`), com emoji onde fizer sentido.
- Paleta: `primary_color` `#FF7A00`, `secondary_color` `#10B981`, fundos neutros.
- Garantir que o último item não fique escondido atrás do bottom nav (padding inferior já é
  calculado em `setupWindowInsets` — revalidar com o novo conteúdo).

---

## ✔️ Checklist

### App
- [ ] Reordenar/inserir todas as seções no `activity_client_home.xml` (cada uma em container com id).
- [ ] Enxugar seção de boas-vindas / reavaliar botão "Ver Serviços".
- [ ] Orquestrar `setup*`/`load*` em `ClientHomeActivity` (cada um isolado e tolerante a falha).
- [ ] Esconder seções vazias (visibility GONE).
- [ ] Pré-aquecer catálogo no `AppApplication` (se ainda não).
- [ ] (Opcional) `SwipeRefreshLayout` com refresh de tudo.
- [ ] Revisar insets/scroll/padding com todo o conteúdo.
- [ ] Consolidar eventos Analytics.

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
