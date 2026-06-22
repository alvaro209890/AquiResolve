# 05 — Busca Inteligente

**Prioridade:** 🟢 Alta · **Fase:** 1 · **Complexidade:** Média · **Status:** ✅ Concluído (2026-06-22)

> Implementado e **validado ao vivo no emulador** (Waydroid): ao digitar (debounce ~250ms) surgem
> sugestões instantâneas do catálogo real; "eletrica" (sem acento) acha "Elétrica"; tocar leva ao
> `CreateOrderActivity` com **nicho + serviço + preço pré-preenchidos** (ex.: "Instalação de chuveiro
> — R$ 150,00"); sem resultado, o CTA abre `ServicesActivity`. **Conclui a Fase 1.**

---

## 🎯 Objetivo

Transformar a busca da Home em um **atalho de contratação**: ao digitar, mostrar sugestões
instantâneas de serviços/nichos e levar o cliente direto ao pedido — apoiando a meta de
"contratar em menos de 30 segundos". **Sem IA** nesta entrega (rápida e barata); a IA é a camada
seguinte (plano `06`).

---

## 🧩 Contexto atual

- A busca hoje (`ClientHomeActivity.performSearch()`) só repassa o texto para `ServicesActivity`
  via `putExtra("search_query", ...)` ao apertar "buscar". Não há sugestões enquanto digita.
- Já existe **`utils/ServiceSearchHelper.kt`** — base de matching de serviços (reusar/estender).
- Fontes de dados: `CatalogRepository` (nichos) + `CatalogServiceRepository`/`catalog_services`
  (serviços com nicho/preço).
- Campo de busca: `etSearch` no `activity_client_home.xml` (dentro de um `MaterialCardView`).

---

## ✅ Escopo

**Entra:**
- ✅ Sugestões instantâneas (debounce ~250ms) ao digitar: serviços e nichos que casam.
- ✅ Matching tolerante a acento/caixa e por `aliases`/`keywords` (já há base no catálogo).
- ✅ Tocar numa sugestão → vai direto ao fluxo de pedido com nicho/serviço pré-selecionado.
- ✅ Estado "nenhum resultado" com CTA (ex.: "Ver todos os serviços" ou "Falar com assistente"
  — gancho para o plano `06`).

**Não entra (⛔):**
- ⛔ Classificação por IA / linguagem natural (é o plano `06`).
- ⛔ Histórico de buscas e busca por voz (futuro).

---

## 🗄 Modelo de dados

**Nenhuma coleção nova.** Usa `service_categories` (nichos) e `catalog_services` (serviços),
já carregados/cacheáveis pelos repositórios existentes.

---

## 📱 App (camadas)

### 1. Lógica de matching
Estender `utils/ServiceSearchHelper.kt`:
- `fun search(query: String, niches: List<...>, services: List<CatalogService>): List<SearchSuggestion>`
- Normalizar (remover acento, lowercase), casar por nome do serviço, nome do nicho e aliases.
- Ranking simples: match exato > começa-com > contém. Limitar a ~8 sugestões.

Modelo de sugestão:
```kotlin
data class SearchSuggestion(
    val label: String,        // ex.: "Instalação de tomada"
    val niche: String,        // ex.: "Elétrica"
    val type: Type            // SERVICE | NICHE
) { enum class Type { SERVICE, NICHE } }
```

### 2. UI de sugestões
- `RecyclerView` (`rvSearchSuggestions`) logo abaixo do card de busca, com `visibility=GONE`
  por padrão; aparece quando há texto + resultados.
- Item `item_search_suggestion.xml`: ícone (🔍/emoji do nicho) + label + nicho em cinza.
- Alternativa simples: usar um `AutoCompleteTextView`, mas o `RecyclerView` dá mais controle visual
  e combina melhor com o layout atual (recomendado).

### 3. Activity (`ClientHomeActivity`)
- `etSearch.addTextChangedListener` com **debounce** (`Handler`/coroutine `Job` cancelável).
- A cada mudança: rodar `ServiceSearchHelper.search(...)` sobre o catálogo em cache → popular adapter.
- Tocar na sugestão:
  - `SERVICE` → fluxo de criação com nicho + serviço pré-selecionados.
  - `NICHE` → fluxo de criação no nicho.
- Apertar "buscar" sem escolher sugestão → comportamento atual (`ServicesActivity` com `search_query`).
- "Nenhum resultado" → mostrar linha com CTA "Não achou? Fale com o assistente 🤖" (liga ao plano `06`)
  ou "Ver todos os serviços".
- Analytics: `busca_sugestao_click` (com label/type), `busca_sem_resultado` (com query).

---

## 🎨 Design / UX

- Dropdown de sugestões com fundo branco, sombra leve, cantos arredondados — colado ao card de busca.
- Destacar no label o trecho que casou (negrito) — opcional, melhora percepção.
- Máx. ~8 itens, scroll se passar.
- Teclado não deve cobrir as sugestões (ajustar `windowSoftInputMode`/scroll se necessário).

---

## ✔️ Checklist

### App
- [x] Estender `ServiceSearchHelper` (`suggest()`: normalização + ranking exato>começa>contém>palavras + limite 8 + complemento estático por sinônimos quando o dinâmico rende pouco).
- [x] Model `SearchSuggestion` (label/niche/type SERVICE|NICHE).
- [x] `item_search_suggestion.xml` + `adapters/SearchSuggestionAdapter.kt`.
- [x] `rvSearchSuggestions` + CTA num card `sectionSearchSuggestions` no `activity_client_home.xml` (topo do conteúdo, GONE por padrão).
- [x] `addTextChangedListener` com debounce (250ms, `Handler` cancelável) em `ClientHomeActivity`.
- [x] Catálogo em cache na Home: `CatalogServiceRepository.loadAll()` (novo) + `allCachedServices()`; pré-aquecido no `AppApplication`.
- [x] Roteamento: SERVICE → `CreateOrderActivity` com `service_category_name` + `preselect_service` (nova chave) + `search_query`; NICHE → só o nicho. `CreateOrderActivity.rebuildServiceTypeAdapter` aplica a pré-seleção (sobrevive ao rebuild assíncrono).
- [x] Estado "sem resultado" com CTA → `ServicesActivity` (gancho p/ assistente IA no plano `06`).
- [x] Eventos Analytics `busca_sugestao_click` / `busca_sem_resultado`.

### QA (validado no emulador Waydroid)
- [x] Digitar mostra sugestões relevantes quase instantâneas (cache em memória, sem Firestore por tecla).
- [x] Acentos/maiúsculas não atrapalham ("eletrica" acha "Elétrica").
- [x] Tocar leva ao pedido certo, pré-preenchido (nicho + serviço + preço).
- [x] Sem resultado mostra CTA, não tela vazia (→ `ServicesActivity`).
- [x] Teclado não cobre as sugestões (dropdown no topo, abaixo da AppBar); limpar o texto fecha o dropdown.
- [x] Offline usa o catálogo em cache/fallback (estático por sinônimos) sem quebrar.

---

## 🟢 Critérios de aceite

1. Ao digitar, sugestões aparecem instantaneamente do catálogo real.
2. Uma sugestão leva ao pedido pré-preenchido (menos toques que a busca atual).
3. Busca sem resultado oferece um próximo passo (não beco sem saída).

---

## ⚠️ Riscos & gotchas

- **Catálogo precisa estar carregado:** garantir `CatalogServiceRepository` em cache na Home
  (carregar em `onCreate`/`AppApplication`); sugestões dependem disso.
- **Debounce obrigatório:** sem ele, busca a cada tecla trava a UI. Cancelar o `Job` anterior.
- **Reusar a chave de extra correta** do nicho/serviço (a mesma que Categorias e a busca atual usam).
- **Performance:** matching é em memória sobre ~300 serviços — trivial; não chamar Firestore por tecla.

---

## 📦 Estimativa

Média — ~1 a 1,5 dia. Reaproveita `ServiceSearchHelper` e o catálogo.
</content>
