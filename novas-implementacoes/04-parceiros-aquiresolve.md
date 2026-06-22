# 04 — Parceiros AquiResolve

**Prioridade:** 🟡 Média · **Fase:** 2 · **Complexidade:** Média

---

## 🎯 Objetivo

Seção **"Parceiros AquiResolve"** na Home: empresas patrocinadoras (ex.: Leroy Merlin, Telhanorte,
Casa & Construção) oferecendo 💰 descontos, 💰 cashback e 💰 cupons aos clientes.

---

## 🧩 Contexto atual

- Home: `ClientHomeActivity.kt` + `activity_client_home.xml`.
- Glide já presente para logos/imagens.
- Padrão de coleção dinâmica + CRUD no painel: espelhar `catalog_services` / banners.
- Não há nada de parceiros hoje — feature nova (mas simples).

---

## ✅ Escopo

**Entra:**
- ✅ Coleção `partners` no Firestore.
- ✅ Seção "Parceiros AquiResolve" na Home (logos + benefício resumido).
- ✅ Tela/sheet de detalhe do parceiro (descrição, benefícios, cupom, link).
- ✅ Tipos de benefício: desconto, cashback, cupom (com código copiável) e/ou link externo.
- ✅ CRUD no painel (`/dashboard/configuracoes/parceiros`).

**Não entra (⛔):**
- ⛔ Integração/validação real de cupom no parceiro (apenas exibe/copia o código).
- ⛔ Tracking de redenção/comissão de afiliado (futuro).
- ⛔ Segmentação por região (campo previsto, sem lógica agora).

---

## 🗄 Modelo de dados — coleção `partners`

```jsonc
{
  "name": "Leroy Merlin",
  "logoUrl": "https://.../leroy.png",
  "bannerUrl": "https://.../leroy-banner.jpg",   // opcional, para o detalhe
  "description": "Materiais de construção e reforma com desconto para clientes AquiResolve.",
  "benefitType": "coupon",   // "discount" | "cashback" | "coupon" | "link"
  "benefitLabel": "10% OFF na primeira compra",
  "couponCode": "AQUI10",    // quando benefitType = coupon
  "url": "https://www.leroymerlin.com.br",  // link do parceiro (opcional)
  "active": true,
  "displayOrder": 1,
  "createdAt": <serverTimestamp>,
  "updatedAt": <serverTimestamp>
}
```

### Regras Firestore

```
// Parceiros patrocinadores — leitura autenticada; escrita só Admin SDK
match /partners/{partnerId} {
  allow read: if isSignedIn();
  allow write: if false;
}
```

---

## 🖥 Painel admin

- **Página:** `dashboard_admin/app/dashboard/configuracoes/parceiros/page.tsx`
  - Form: nome, logo (upload/URL), banner opcional, descrição, `benefitType` (select),
    `benefitLabel`, `couponCode` (condicional), URL, ordem, ativo.
  - Lista com preview de logo + toggle ativo + reordenar.
- **API:** `dashboard_admin/app/api/partners/route.ts` (GET/POST/DELETE, Admin SDK).
- **Sidebar:** item em Configurações.

---

## 📱 App (camadas)

### 1. Model
`app/src/main/java/com/aquiresolve/app/models/Partner.kt`
```kotlin
data class Partner(
    val id: String = "",
    val name: String = "",
    val logoUrl: String = "",
    val bannerUrl: String = "",
    val description: String = "",
    val benefitType: String = "link",
    val benefitLabel: String = "",
    val couponCode: String = "",
    val url: String = "",
    val active: Boolean = true,
    val displayOrder: Int = 0
)
```

### 2. Repository
`app/src/main/java/com/aquiresolve/app/PartnerRepository.kt` — `load()` lê `partners`, filtra
`active`, ordena, cacheia, fallback vazio (esconde seção).

### 3. Adapter + detalhe
- `adapters/PartnerAdapter.kt` + `item_partner.xml` (card com logo + `benefitLabel`).
- Detalhe: bottom-sheet/`PartnerDetailActivity` com banner, descrição, benefício,
  **botão "Copiar cupom"** (`ClipboardManager`) quando `coupon`, e "Visitar site" quando há `url`.

### 4. Layout (Home)
Seção "Parceiros AquiResolve" (título + `RecyclerView` horizontal de logos), abaixo de Combos.

### 5. Activity (`ClientHomeActivity`)
- `setupPartners()` via `PartnerRepository`.
- Toque → detalhe. Copiar cupom → `ClipboardManager` + toast "Cupom copiado".
- Link → `Intent.ACTION_VIEW`.
- Analytics: `parceiro_click` (+ `parceiro_cupom_copiado`).

---

## 🎨 Design / UX

- Cards de logo em fundo branco/neutro, cantos arredondados, logo centralizado + label do benefício
  em 1 linha (`secondary_color` para reforçar "vantagem").
- Detalhe limpo: banner no topo, benefício em destaque, cupom em "pill" com botão copiar.
- Ícone 💰 nos benefícios para reforço visual.

---

## ✔️ Checklist

### Firestore / Regras
- [ ] Bloco `partners` em `firestore.rules` + publicar.

### Painel admin
- [ ] API `app/api/partners/route.ts` (GET/POST/DELETE, Admin SDK).
- [ ] Página `dashboard/configuracoes/parceiros/page.tsx`.
- [ ] Item na sidebar + deploy.
- [ ] Cadastrar 2–3 parceiros de teste.

### App
- [ ] Model `Partner.kt`.
- [ ] `PartnerRepository.kt`.
- [ ] `PartnerAdapter.kt` + `item_partner.xml`.
- [ ] Seção na Home (título + RecyclerView).
- [ ] Detalhe do parceiro (copiar cupom / abrir link).
- [ ] `setupPartners()` em `ClientHomeActivity`.
- [ ] Eventos Analytics `parceiro_click` / `parceiro_cupom_copiado`.

### QA
- [ ] Parceiros aparecem; vazio → seção some.
- [ ] Copiar cupom funciona (cola no clipboard + toast).
- [ ] Link abre no navegador.
- [ ] Logos carregam (Glide) e não distorcem.

---

## 🟢 Critérios de aceite

1. Admin cadastra um parceiro no painel; aparece na Home sem novo APK.
2. Cliente vê o benefício, copia o cupom e/ou acessa o site do parceiro.
3. Home sem parceiros não mostra seção vazia.

---

## ⚠️ Riscos & gotchas

- **Logos de marcas:** garantir autorização de uso das marcas dos parceiros (jurídico/comercial) —
  não é problema técnico, mas registrar como pré-requisito de conteúdo.
- **Proporção de logos:** padronizar exibição (centerInside + fundo branco) para logos de tamanhos
  diferentes não ficarem feios.
- **Cupom vazio:** se `benefitType=coupon` sem `couponCode`, esconder o botão copiar.

---

## 📦 Estimativa

Média (baixa, na prática) — ~1 dia (app) + ~0,5 a 1 dia (painel).
</content>
