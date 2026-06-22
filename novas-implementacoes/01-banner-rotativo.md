# 01 — Banner Rotativo (Carrossel da Home)

**Prioridade:** 🟢 Alta · **Fase:** 1 · **Complexidade:** Baixa-Média

---

## 🎯 Objetivo

Exibir, no topo da Home do cliente, um **carrossel rotativo** que comunica cashback,
promoções, combos e parceiros — banners gerenciados pelo painel admin (sem novo APK).

---

## 🧩 Contexto atual

- Home: `app/src/main/java/com/aquiresolve/app/ClientHomeActivity.kt` + layout
  `app/src/main/res/layout/activity_client_home.xml` (um `NestedScrollView` > `LinearLayout` vertical).
  O banner entra **logo abaixo da AppBar / acima da seção de boas-vindas**.
- `ViewPager2 1.0.0` **já está no `app/build.gradle`** → usar ele (sem lib externa de carrossel).
- `Glide 4.16.0` já presente → carregamento das imagens.
- Padrão de catálogo dinâmico para espelhar: `CatalogRepository.kt` (lê coleção, cacheia, fallback).

---

## ✅ Escopo

**Entra:**
- ✅ Coleção `home_banners` no Firestore (gerida pelo painel).
- ✅ Carrossel com auto-scroll (~4s), indicadores (dots) e swipe manual.
- ✅ Ação por banner: abrir nicho/serviço, combos, parceiros, cashback ou URL externa.
- ✅ Estados: loading (placeholder), vazio (esconde a seção), erro (esconde).
- ✅ CRUD no painel (`/dashboard/configuracoes/banners`) via Admin SDK.

**Não entra (⛔):**
- ⛔ Segmentação por usuário/cidade (fase futura — campo já previsto, mas sem lógica agora).
- ⛔ Agendamento automático por data (campo `startsAt/endsAt` previsto, lógica simples opcional).
- ⛔ Métricas avançadas de impressão (só clique via Analytics nesta entrega).

---

## 🗄 Modelo de dados — coleção `home_banners`

Um documento por banner:

```jsonc
{
  "title": "Ganhe cashback em todo serviço",      // texto opcional sobre a imagem
  "subtitle": "Até 8% de volta",                   // opcional
  "imageUrl": "https://.../banner1.jpg",           // obrigatório (Storage ou URL)
  "actionType": "niche",   // "niche" | "service" | "combos" | "partners" | "cashback" | "url" | "none"
  "actionValue": "Elétrica", // nicho/serviço/URL conforme actionType (ignorado em combos/partners/cashback/none)
  "backgroundColor": "#FF7A00", // fallback enquanto a imagem carrega (opcional)
  "active": true,
  "displayOrder": 1,
  "startsAt": null,        // Timestamp opcional (futuro)
  "endsAt": null,          // Timestamp opcional (futuro)
  "createdAt": <serverTimestamp>,
  "updatedAt": <serverTimestamp>
}
```

### Regras Firestore (`firestore.rules`)

Seguir o padrão do projeto (leitura autenticada, escrita só Admin SDK):

```
// Banners da Home — leitura por usuário autenticado; escrita só via Admin SDK (painel)
match /home_banners/{bannerId} {
  allow read: if isSignedIn();
  allow write: if false;
}
```

> Publicar com `firebase deploy --only firestore:rules --project aqui-resolve`.

---

## 🖥 Painel admin

- **Página:** `dashboard_admin/app/dashboard/configuracoes/banners/page.tsx`
  - Lista de banners (ordenável por `displayOrder`), toggle `active`, preview da imagem.
  - Formulário: upload de imagem (Storage) **ou** colar URL, título/subtítulo, `actionType` (select),
    `actionValue` (condicional ao tipo), cor de fundo, ordem.
- **API:** `dashboard_admin/app/api/banners/route.ts` (GET/POST/DELETE) — Admin SDK, espelhando
  o padrão de `app/api/catalog/services/route.ts`.
  - `GET` lista; `POST` cria/atualiza (id determinístico ou auto); `DELETE ?id=` remove.
- **Sidebar:** adicionar item em `dashboard_admin/components/layout/sidebar.tsx` (grupo Configurações).

---

## 📱 App (camadas)

### 1. Model
`app/src/main/java/com/aquiresolve/app/models/HomeBanner.kt`
```kotlin
data class HomeBanner(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val imageUrl: String = "",
    val actionType: String = "none",
    val actionValue: String = "",
    val backgroundColor: String = "",
    val active: Boolean = true,
    val displayOrder: Int = 0
)
```

### 2. Repository
`app/src/main/java/com/aquiresolve/app/BannerRepository.kt` (espelhar `CatalogRepository`):
- `suspend fun load(): List<HomeBanner>` lê `home_banners`, filtra `active`, ordena por `displayOrder`.
- Cache em memória + fallback (lista vazia → seção escondida).
- (Opcional) pré-carregar no `AppApplication`.

### 3. Adapter
`app/src/main/java/com/aquiresolve/app/adapters/BannerAdapter.kt`
- `RecyclerView.Adapter` para `ViewPager2`.
- Item: `app/src/main/res/layout/item_home_banner.xml` (MaterialCardView, ImageView com Glide,
  overlay de título/subtítulo).
- Callback `onClick(banner)` para o roteamento.

### 4. Layout
Inserir no `activity_client_home.xml`, no topo do `LinearLayout` do conteúdo:
```xml
<!-- Carrossel de banners -->
<androidx.viewpager2.widget.ViewPager2
    android:id="@+id/bannerPager"
    android:layout_width="match_parent"
    android:layout_height="160dp"
    android:layout_marginBottom="12dp" />

<LinearLayout
    android:id="@+id/bannerDots"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:layout_gravity="center_horizontal"
    android:orientation="horizontal"
    android:layout_marginBottom="20dp" />
```
> Manter tudo num container que pode ser escondido (`visibility = GONE`) quando não houver banners.

### 5. Activity (`ClientHomeActivity`)
- `setupBannerCarousel()`: carrega via `BannerRepository`, popula adapter, monta dots.
- **Auto-scroll:** `Handler`/`lifecycleScope` com loop a cada ~4s; pausar em `onPause`, retomar em `onResume`; cancelar ao tocar (swipe manual).
- **Roteamento por `actionType`:**
  - `niche` → `ServicesActivity`/`CreateOrderActivity` com extra do nicho.
  - `service` → fluxo de criação com serviço pré-selecionado.
  - `combos` → seção/Activity de combos (plano `03`).
  - `partners` → seção/Activity de parceiros (plano `04`).
  - `cashback` → `CashbackActivity` (já existe).
  - `url` → `Intent.ACTION_VIEW` (navegador).
  - `none` → sem ação.
- Analytics: `home_banner_click` com `actionType`/`actionValue`.

---

## 🎨 Design / UX

- Altura ~160dp, cantos arredondados (16dp, igual aos cards atuais), elevação leve.
- Paleta do app: `primary_color` `#FF7A00`, `secondary_color` `#10B981`.
- Dots: ativo = `primary_color`, inativo = `gray_300`.
- Texto sobre imagem com leve gradiente/overlay para legibilidade.
- Loading: card com `backgroundColor` do banner enquanto a imagem do Glide carrega.

---

## ✔️ Checklist

### Firestore / Regras
- [ ] Adicionar bloco `home_banners` em `firestore.rules`.
- [ ] Publicar regras (`firebase deploy --only firestore:rules --project aqui-resolve`).
- [ ] (Opcional) Índice por `displayOrder` se necessário.

### Painel admin
- [ ] API `app/api/banners/route.ts` (GET/POST/DELETE, Admin SDK).
- [ ] Página `dashboard/configuracoes/banners/page.tsx` (lista + form + upload/URL).
- [ ] Item na `sidebar.tsx`.
- [ ] Deploy do painel (`npx vercel deploy --prod --yes`).
- [ ] Cadastrar 3–4 banners de teste.

### App
- [ ] Model `HomeBanner.kt`.
- [ ] `BannerRepository.kt` (load + cache + fallback).
- [ ] `BannerAdapter.kt` + `item_home_banner.xml`.
- [ ] Inserir `ViewPager2` + dots no `activity_client_home.xml`.
- [ ] `setupBannerCarousel()` + auto-scroll + dots em `ClientHomeActivity`.
- [ ] Roteamento por `actionType`.
- [ ] Esconder seção quando vazio/erro.
- [ ] Evento Analytics `home_banner_click`.

### QA
- [ ] Carrossel rotaciona sozinho e pausa ao interagir.
- [ ] Swipe manual funciona; dots acompanham.
- [ ] Cada `actionType` abre o destino correto.
- [ ] Sem banners → seção some, Home não quebra.
- [ ] Offline → não trava (fallback).
- [ ] Insets/scroll continuam corretos (topo sob a AppBar).

---

## 🟢 Critérios de aceite

1. Admin cadastra um banner no painel e ele aparece no app **sem novo APK**.
2. Tocar no banner leva ao destino configurado.
3. Home sem banners não exibe espaço vazio nem erro.

---

## ⚠️ Riscos & gotchas

- **Auto-scroll x vazamento:** sempre cancelar o loop em `onPause`/`onDestroy` (o app já tem cuidado
  com listeners — ver `centralUnreadListener`).
- **Imagens grandes:** usar Glide com `DiskCacheStrategy.ALL` e dimensão fixa; recomendar ao admin
  banners ~1200×500.
- **`actionValue` inválido** (nicho que não existe): tratar como `none` para não crashar.
- **ViewPager2 dentro de NestedScrollView:** altura fixa (160dp) evita conflito de medição.

---

## 📦 Estimativa

Baixa-Média — ~1 a 1,5 dia (app) + ~0,5 dia (painel). Sem libs novas.
</content>
