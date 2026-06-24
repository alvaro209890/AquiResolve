# Correção: Preço desatualizado em Combos + Expectativa falsa em Parceiros

**Data:** 2026-06-24  
**Componentes:** App Mobile — `HomeComboAdapter`, `ComboDetailActivity`, `PartnerDetailActivity`  
**Severidade:** Alta (combo) / Média (parceiro)

---

## Bug #1 — Preço exibido no combo pode divergir do preço cobrado

### Sintoma

O banner de combo na Home e a tela de detalhe exibiam `fullPrice`/`promoPrice`/`savings` que foram
gravados no Firestore no momento em que o admin **criou** o combo. Se posteriormente o admin
alterar o preço de qualquer serviço no catálogo (`catalog_services`), os valores exibidos ficam
desatualizados — mas o carrinho cobra o preço atual do catálogo.

**Exemplo:** Combo criado com preço total R$500 → promo R$400. Admin atualiza serviço de R$200
para R$250. Banner continua mostrando R$400, mas o carrinho soma R$550 e aplica 10% → cobra R$495.
O cliente paga R$495 e esperava R$400.

### Causa Raiz

Dois locais consumiam os campos estáticos do documento `home_combos`:

| Local | Campo usado (stale) |
|---|---|
| `HomeComboAdapter.onBindViewHolder` | `combo.fullPrice`, `combo.promoPrice`, `combo.savings` |
| `ComboDetailActivity.renderSummary` | `combo.fullPrice` (com fallback), `combo.promoPrice`, `combo.savings` |

### Correção

**`HomeComboAdapter.kt`:**
- Adicionado helper `resolveItemPrice(item: HomeComboItem)` que resolve o preço via
  `CatalogServiceRepository.findService()` (síncrono — lê o `ConcurrentHashMap` já em memória,
  pré-aquecido no `AppApplication`) com fallback em `ServicePricing`.
- `onBindViewHolder` agora calcula `liveFullPrice`, `livePromoPrice` e `liveSavings` ao vivo,
  aplicando `combo.discountPercent` sobre a soma dos preços atuais.
- Os campos `combo.fullPrice`/`promoPrice`/`savings` não são mais usados para exibição.

**`ComboDetailActivity.kt` — `renderSummary()`:**
- Removida a lógica condicional `if (combo.fullPrice > 0) combo.fullPrice else sumItems`.
- `full` agora é sempre `rows.sumOf { it.price }` (soma dos preços resolvidos do catálogo pela
  função `resolveItemPrice()` que já existia na activity).
- `promo` calculado exclusivamente a partir de `combo.discountPercent` sobre o `full` ao vivo.
- `savings` recalculado a partir de `full - promo`.

### Por que `CatalogServiceRepository.findService()` é seguro no adapter

`findService()` lê um `ConcurrentHashMap` em memória — operação síncrona O(1), sem I/O.
O catálogo é pré-aquecido pelo `AppApplication.onCreate()` via `CatalogServiceRepository.loadAll()`
antes de qualquer tela ser exibida. O fallback em `ServicePricing` (tabela estática) cobre o caso
raro de cache vazio (ex.: primeira abertura offline).

---

## Bug #5 — Benefício discount/cashback em parceiros cria expectativa falsa

### Sintoma

Parceiros com `benefitType = discount` ou `benefitType = cashback` exibiam apenas o `benefitLabel`
(ex.: "💰 10% de desconto") sem nenhuma explicação de como aproveitá-lo. Usuários poderiam
interpretar que o desconto/cashback seria aplicado automaticamente ao contratar via AquiResolve —
o que não ocorre. Esses benefícios são concedidos pelo parceiro diretamente, fora do fluxo do app.

### Diferença entre os tipos de benefício

| Tipo | Mecânica no app | Ação disponível |
|---|---|---|
| `coupon` | Exibe código copiável | Copiar cupom → usar no site do parceiro |
| `link` | Exibe URL | Abre o site do parceiro |
| `discount` | Só texto informativo | **Nenhuma** — expectativa falsa |
| `cashback` | Só texto informativo | **Nenhuma** — expectativa falsa |

### Correção

**`activity_partner_detail.xml`:**
- Adicionado `TextView` com id `tvBenefitExplanation`, inicialmente `visibility="gone"`.
- Fundo âmbar `#FFF8E1` e texto laranja `#E65100` — padrão de aviso informativo.

**`PartnerDetailActivity.kt`:**
- Adicionado método `partnerSideBenefitExplanation(benefitType)` que retorna o texto explicativo
  para `discount` e `cashback`, e `null` para os demais tipos.
- Em `bind()`, mostra/esconde `tvBenefitExplanation` conforme o retorno do helper.

**Texto exibido:**
- Desconto: *"ℹ️ Este desconto é concedido diretamente pelo parceiro. Para aproveitá-lo, apresente-se
  como cliente AquiResolve ao contratar o serviço ou acesse o site do parceiro."*
- Cashback: *"ℹ️ Este cashback é concedido diretamente pelo parceiro. Para aproveitá-lo, apresente-se
  como cliente AquiResolve ao contratar o serviço ou acesse o site do parceiro."*

---

## Arquivos Alterados

| Arquivo | Tipo de mudança |
|---|---|
| `app/.../adapters/HomeComboAdapter.kt` | Preços calculados ao vivo; novo helper `resolveItemPrice` |
| `app/.../ComboDetailActivity.kt` | `renderSummary` sempre usa preços live do catálogo |
| `app/.../PartnerDetailActivity.kt` | Aviso contextual para benefícios partner-side |
| `app/.../res/layout/activity_partner_detail.xml` | Novo `tvBenefitExplanation` |

---

## Deploy Necessário

Todas as mudanças são **exclusivamente no app Android**. É necessário:

```bash
cd app
./gradlew assembleDebug   # ou assembleRelease para produção
```

O painel (Vercel) e o backend (Render) **não precisam de redeploy** — nenhum arquivo de servidor
foi alterado.

---

## Como Evitar no Futuro

1. **Combos:** nunca confiar em campos de preço armazenados em documentos Firestore quando o
   catálogo pode ser atualizado independentemente. Sempre recalcular a partir da fonte primária
   (`catalog_services`) no momento da exibição.

2. **Parceiros:** ao adicionar um novo `benefitType`, avaliar se ele tem mecânica concreta no app.
   Se não tiver, exibir aviso explicativo no detalhe. Tipos com ação concreta (`coupon`, `link`)
   não precisam de aviso.
