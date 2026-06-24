# Tema Escuro (Dark Mode) — App Mobile

**Data:** 2026-06-24  
**Componente:** App Android (`app/`)  
**Status:** ✅ Funcional e validado (build `assembleDebug` OK)

---

## Visão Geral

O app suporta **tema claro, escuro e "seguir o sistema"**:

- **Padrão:** segue a configuração de tema do celular (claro/escuro do Android).
- **Escolha manual:** o usuário pode forçar Claro ou Escuro em **Perfil → Aparência**.
- A escolha é **persistida** e reaplicada no startup, antes de qualquer tela aparecer.

A implementação usa o mecanismo nativo do Android
(`AppCompatDelegate.setDefaultNightMode`), que recria as Activities automaticamente
ao trocar de modo — sem `recreate()` manual nem flicker.

---

## Arquitetura

### 1. `ThemeManager` (`app/.../ThemeManager.kt`)

Objeto único que centraliza a lógica de tema:

| Método | Função |
|---|---|
| `current(context)` | Lê o modo salvo (default = `SYSTEM`) |
| `apply(context, mode)` | Persiste a escolha + aplica via `setDefaultNightMode` |
| `applyStored(context)` | Reaplica o modo salvo no startup |
| `label(mode)` | Rótulo legível ("Sistema padrão" / "Claro" / "Escuro") |

Modos (`ThemeManager.Mode`): `SYSTEM`, `LIGHT`, `DARK`, mapeados para
`MODE_NIGHT_FOLLOW_SYSTEM` / `MODE_NIGHT_NO` / `MODE_NIGHT_YES`.
Persistência em `SharedPreferences` (`theme_prefs` / `theme_mode`).

### 2. Startup (`AppApplication.onCreate`)

`ThemeManager.applyStored(this)` é a **primeira** linha de `onCreate`, garantindo
que o modo correto já esteja ativo antes de qualquer Activity inflar.

### 3. Seletor in-app (`ProfileActivity`)

Linha **"Aparência"** no Perfil (`tvThemeValue` mostra o modo atual). Ao tocar,
abre um diálogo de escolha única (Sistema / Claro / Escuro) que chama
`ThemeManager.apply()` e atualiza o subtítulo.

### 4. Tema base (`res/values/themes.xml`)

`Theme.LoginApp` estende **`Theme.Material3.DayNight.NoActionBar`**. O parent
DayNight aplica automaticamente os defaults escuros dos widgets Material no modo
noturno; os atributos explícitos do tema referenciam **tokens semânticos de cor**
que trocam de valor em `values-night/`.

### 5. Paletas (`res/values/colors.xml` + `res/values-night/colors.xml`)

`values-night/colors.xml` **sobrescreve apenas os tokens semânticos** — fundos,
superfícies, texto, divisórias e a escala de cinza. Cores de marca/literais
(`primary_*`, `secondary_*`, status, nichos, `white`, `black`) **não** são
redefinidas: permanecem iguais nos dois modos, por design.

**Regra de ouro:** `@color/white` continua branco (para texto/ícone sobre cor de
marca). O que vira superfície escura é `@color/surface_color` / `card_background`
/ `background_*`.

**Truque da rampa de cinza invertida:** no escuro, `gray_100..300` (usados como
fundo/divisória) ficam escuros e `gray_400..600` (usados como ícone/texto) ficam
claros — assim ambos os usos ficam corretos sem tocar em nenhum layout.

---

## Polimento aplicado em 2026-06-24

A base do tema (itens 1–5 acima) já existia. Esta rodada **corrigiu cards de
status/info que tinham cor de fundo pastel hardcoded** e quebravam no escuro.

### Bug corrigido (crítico)

O **banner de verificação do prestador** (`activity_provider_home.xml`) tinha fundo
`#FFF8E1` (âmbar claro) fixo, mas o texto usava `@color/text_primary`. No modo
escuro o texto vira **claro** → texto claro sobre âmbar claro = **ilegível**.

### Solução: superfícies semânticas tonalizadas

Criados 5 tokens de cor com variante clara e escura:

| Token | Claro | Escuro | Uso |
|---|---|---|---|
| `surface_success` | `#E8F5E9` | `#16271C` | Cards de sucesso/reembolso |
| `surface_warning` | `#FFF8E1` | `#2A2510` | Banner de verificação, avisos |
| `surface_info` | `#F0F8FF` | `#16243A` | Card de info (depósito PIX) |
| `surface_highlight` | `#FFF3E6` | `#2A2417` | Caixa de atenção (código de conclusão) |
| `warning_text` | `#E65100` | `#FFB74D` | Texto âmbar sobre `surface_warning` |

No escuro são bases escuras com leve matiz da cor de status — o texto e as cores
de status (verde/laranja) ficam legíveis, e os cards deixam de ser "manchas
brilhantes" num fundo escuro.

### Layouts ajustados (hex → token semântico)

| Arquivo | Card | Antes | Depois |
|---|---|---|---|
| `activity_provider_home.xml` | Banner de verificação | `#FFF8E1` | `@color/surface_warning` |
| `activity_provider_home.xml` | Info depósito PIX | `#F0F8FF` | `@color/surface_info` |
| `activity_order_details.xml` | Caixa código de conclusão | `#B3FFFFFF` | `@color/surface_highlight` |
| `activity_order_details.xml` | Card de reembolso | `#E8F5E9` | `@color/surface_success` |
| `activity_payment_confirmation.xml` | Próximos passos | `#E8F5E9` | `@color/surface_success` |
| `activity_partner_detail.xml` | Aviso de benefício do parceiro | `#FFF8E1` / `#E65100` | `@color/surface_warning` / `@color/warning_text` |

---

## Auditoria de robustez (feita nesta rodada)

Varredura completa dos 89 layouts confirmou que **não há**:
- fundo branco hardcoded (`#FFFFFF`, `@android:color/white`) em telas/cards;
- texto preto/escuro hardcoded (`@color/black`, `#000`, `#1E293B`) que sumiria no escuro;
- drawables com `<solid>` branco como fundo (exceto `signature_border.xml`, que é o
  pad de assinatura — proposital).

Os dois únicos fundos pretos (`activity_image_preview.xml`, `item_image_preview.xml`)
são telas de visualização de imagem, onde preto é correto nos dois modos.

---

## Validação

- `./gradlew :app:processDebugResources` → **BUILD SUCCESSFUL** (cores e variantes
  day/night compilam e mergeiam).
- `./gradlew :app:assembleDebug` → **BUILD SUCCESSFUL** (APK completo, Kotlin + linking).

---

## Deploy

Mudança **exclusivamente no app Android** → requer **novo APK**
(`./gradlew assembleDebug` / `bundleRelease`). Painel (Vercel) e backend (Render)
não são afetados.

---

## Como manter

Ao criar um card de status/info novo, **use os tokens semânticos**
(`surface_success` / `surface_warning` / `surface_info` / `surface_highlight`),
nunca um hex pastel hardcoded — caso contrário ele quebra no modo escuro. Para
fundos de tela/card use `background_*` / `surface_color` / `card_background`; para
texto use `text_primary` / `text_secondary`. Cores de marca e status podem ser
literais (são iguais nos dois modos).
