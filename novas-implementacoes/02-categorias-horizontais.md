# 02 — Categorias Horizontais

**Prioridade:** 🟢 Alta · **Fase:** 1 · **Complexidade:** Baixa

---

## 🎯 Objetivo

Substituir a navegação atual da Home por **categorias (nichos) em scroll horizontal**, com
ícone/emoji e nome — visual moderno e contratação rápida (tocar no nicho → criar pedido).

Exemplo: ⚡ Elétrica · 🚿 Hidráulica · 🛋 Limpeza · ❄️ Ar-condicionado · 🔑 Chaveiro · 🪑 Montagem

---

## 🧩 Contexto atual

- **O catálogo de nichos já é dinâmico:** `CatalogRepository.kt` lê `service_categories`
  (mantida no painel em `/dashboard/servicos/catalogo-app`), com fallback estático em
  `utils/ServiceNicheCatalog.kt`. **Reusar isso** — não criar coleção nova.
- Já existe `adapters/ServiceCategoriesAdapter.kt` + `item_service_category.xml` (usados na
  `ServicesActivity`) — base para o adapter horizontal (adaptar layout do item, não recriar do zero).
- `CatalogRepository.CatalogNiche` já traz `name`, `aliases`, `displayOrder`, `icon`.
- Home: `ClientHomeActivity.kt` + `activity_client_home.xml`.

---

## ✅ Escopo

**Entra:**
- ✅ `RecyclerView` horizontal de nichos na Home, alimentado por `CatalogRepository`.
- ✅ Ícone por nicho (emoji ou drawable mapeado do campo `icon`).
- ✅ Toque → fluxo de criação de pedido com o nicho pré-selecionado.
- ✅ Item "Ver todos" no fim → `ServicesActivity` (lista completa).
- ✅ Fallback estático quando Firestore vazio/offline (já garantido pelo repo).

**Não entra (⛔):**
- ⛔ Reordenação por popularidade/uso (futuro).
- ⛔ Nova coleção (reusa `service_categories`).

---

## 🗄 Modelo de dados

**Nenhuma coleção nova.** Usa `service_categories` (já existente). Campos relevantes lidos por
`CatalogRepository`: `name`/`title`/`label`, `active`/`isActive`/`enabled`, `displayOrder`/`order`,
`icon`, `aliases`/`keywords`.

> Se quisermos emojis bonitos por nicho, o campo `icon` no painel pode receber:
> - um nome de drawable já existente (mapear no app), **ou**
> - um emoji literal (renderizar direto num `TextView`).
> Recomendado: **emoji literal** no `icon` para flexibilidade total sem novo APK.

### Ação no painel (opcional, melhora UX de gestão)
- Garantir que a aba **Nichos** de `/dashboard/servicos/catalogo-app` permita editar o campo `icon`
  (emoji) e `displayOrder`. Se já permite, nada a fazer no painel.

---

## 📱 App (camadas)

### 1. Adapter
`app/src/main/java/com/aquiresolve/app/adapters/HomeCategoryAdapter.kt`
- Lista de `CatalogRepository.CatalogNiche` (+ item sintético "Ver todos").
- Item layout: `app/src/main/res/layout/item_home_category.xml`
  - `MaterialCardView` circular/arredondado com `TextView` de emoji (ou `ImageView` se drawable) + label abaixo.
- Callback `onClick(niche)` / `onSeeAll()`.

### 2. Layout (Home)
Inserir seção no `activity_client_home.xml` (abaixo do banner, antes de "Pedidos Recentes"):
```xml
<TextView
    android:id="@+id/tvCategoriesTitle"
    android:text="Categorias"
    android:textSize="20sp" android:textStyle="bold"
    android:textColor="@color/text_primary"
    android:layout_width="match_parent" android:layout_height="wrap_content"
    android:layout_marginBottom="12dp" />

<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/rvCategories"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:clipToPadding="false"
    android:orientation="horizontal"
    app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager"
    android:layout_marginBottom="24dp" />
```
(definir `LinearLayoutManager(HORIZONTAL)` no código ou via `app:layoutManager` + orientação).

### 3. Activity (`ClientHomeActivity`)
- `setupCategories()`: usa `CatalogRepository.cachedNicheNames()` ou `load()` (suspend) no `lifecycleScope`.
- `LinearLayoutManager(this, HORIZONTAL, false)`.
- Toque no nicho → `Intent` para o fluxo de criação com extra do nicho (mesmo extra que a busca já
  usa: `putExtra("search_query"/nicho)` → verificar como `ServicesActivity`/`CreateOrderActivity`
  recebem o nicho hoje e reusar a mesma chave).
- "Ver todos" → `ServicesActivity`.
- Analytics: `home_categoria_click` com nome do nicho.

---

## 🎨 Design / UX

- Cada item ~72–88dp de largura: círculo/card com emoji 28–32sp + label 12–13sp em 1–2 linhas.
- Espaçamento horizontal entre itens ~12dp; padding lateral de 16dp (alinhado à Home).
- Card ativo com leve elevação; cor neutra de fundo, label `text_primary`.
- Scroll suave; `clipToPadding=false` para o primeiro/último não colarem na borda.

---

## ✔️ Checklist

### Painel admin
- [ ] Conferir/editar campo `icon` (emoji) e `displayOrder` na aba Nichos do catálogo.
- [ ] Definir emojis para os nichos principais.

### App
- [ ] `item_home_category.xml`.
- [ ] `HomeCategoryAdapter.kt`.
- [ ] Inserir título + `RecyclerView` horizontal no `activity_client_home.xml`.
- [ ] `setupCategories()` em `ClientHomeActivity` (load via `CatalogRepository`).
- [ ] Roteamento nicho → criação de pedido com nicho pré-selecionado.
- [ ] Item "Ver todos" → `ServicesActivity`.
- [ ] Renderizar emoji do campo `icon` (fallback ⚙️/wrench se vazio).
- [ ] Evento Analytics `home_categoria_click`.

### QA
- [ ] Lista carrega do Firestore; offline cai no fallback estático sem quebrar.
- [ ] Tocar num nicho abre criação já no nicho certo.
- [ ] "Ver todos" abre a lista completa.
- [ ] Emojis aparecem; nicho sem `icon` usa fallback.
- [ ] Scroll horizontal fluido, sem cortar itens nas bordas.

---

## 🟢 Critérios de aceite

1. Categorias aparecem em scroll horizontal alimentadas pelo catálogo do painel.
2. Tocar leva direto ao pedido no nicho certo (menos toques que hoje).
3. Editar emoji/ordem no painel reflete no app **sem novo APK** (dado dinâmico).

---

## ⚠️ Riscos & gotchas

- **Chave do extra do nicho:** reusar exatamente a que `ServicesActivity`/`CreateOrderActivity` já
  esperam (não inventar nova) — conferir no código antes de codar.
- **Matching prestador↔pedido:** o nicho selecionado precisa bater com o nome no catálogo
  (`ServiceNicheCatalog`); como já vem do mesmo repo, fica consistente.
- **Fallback:** `CatalogRepository.cachedNicheNames()` já devolve `providerSelectableNiches` quando
  vazio — usar para nunca exibir lista vazia.

---

## 📦 Estimativa

Baixa — ~0,5 a 1 dia. Reaproveita catálogo e adapter existentes.
</content>
