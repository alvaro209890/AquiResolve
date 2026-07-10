# Correção de layout — barras do sistema cobrindo o app (insets/edge-to-edge)

**Data:** 2026-07-09 · **Escopo:** app mobile inteiro (todas as ~57 activities)

## Sintomas relatados

1. **Androids antigos:** erro de layout em várias páginas — conteúdo do topo (título,
   botão voltar) escondido embaixo da barra de status/relógio.
2. **Androids novos (15+):** as barras do sistema (status em cima; navegação embaixo —
   gestos ou 3 botões ⏴⏺⏹) **cobrem** o topo e o rodapé do app em todas as telas.

## Causas raiz (eram 4, acumuladas)

| # | Causa | Efeito |
|---|-------|--------|
| 1 | Tema global com `android:windowTranslucentStatus=true` | TODAS as telas eram desenhadas **sob** a barra de status em qualquer Android, e só 3 activities (ClientHome, ProviderHome, Services) compensavam com insets. As outras ~54 ficavam com o topo coberto. |
| 2 | 10 activities (login, cadastros, ratings, HomeActivity, ImagePreview, CreateOrder, ForgotPassword) com hack `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN` / `setDecorFitsSystemWindows(false)` sem nenhuma compensação | Topo coberto nessas telas, em qualquer versão. |
| 3 | `InsetsHelper` com bug: com `bottomView = null` (4 dos 5 usos), o 2º `setOnApplyWindowInsetsListener` na MESMA view **substituía** o 1º (só existe 1 listener por view) → o padding do topo se perdia | OrderDetails, AssistantChat, ClientOrders e CreateOrder sem inset de topo. |
| 4 | **Android 15+ (targetSdk 35) força edge-to-edge** e ignora `statusBarColor`/`navigationBarColor` | Toda tela sem tratamento de insets fica com topo E rodapé cobertos pelas barras. Explica o sintoma nos "androids novos". |

Bônus: o `FloatingMicHelper` registrava listener de insets **no decorView**, o que
suprime o `onApplyWindowInsets` interno do DecorView (que desenha o fundo das barras)
nas 8 telas com microfone flutuante.

## Correção (estratégia em 2 regimes)

### Android < 15 — o sistema volta a cuidar do layout
- `themes.xml`: **removido** `windowTranslucentStatus`; `statusBarColor` vira
  `@color/primary_color` (laranja da marca, ícones brancos). Sem a flag translucent,
  o sistema posiciona o conteúdo entre as barras sozinho — todas as ~54 telas sem
  tratamento ficam corretas de uma vez.
- Removidos os 10 hacks fullscreen copy-paste (bloco `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN`).
- Removido o `InsetsHelper.kt` (bugado) e suas 5 chamadas.

### Android 15+ — tratador GLOBAL de insets (`EdgeToEdgeInsets.kt`)
Registrado uma única vez no `AppApplication` via `ActivityLifecycleCallbacks`
(`onActivityPostCreated`), vale para **toda** activity atual e futura:
- Padding em `android.R.id.content` com os insets de `systemBars() + displayCutout()`.
- Quando a janela usa `adjustResize`, o rodapé também respeita o teclado
  (`ime()`) — o adjustResize clássico não funciona em janela edge-to-edge.
- Desenha **faixas coloridas** atrás das barras (Android 15 ignora
  `statusBarColor`): topo = cor solicitada pela tela se opaca, senão
  `primary_color`; rodapé = `surface_color` (adapta ao tema escuro).
- **CONSOME** os insets — impede que componentes Material (BottomNavigationView)
  apliquem o mesmo inset de novo (causa histórica da barra inferior "bugada",
  ver correção de 2026-06-24).

### Marker interfaces (em `EdgeToEdgeInsets.kt`)
- `InsetsSelfManaged` — activity que já trata os próprios insets; o tratador global a
  **pula**. Implementada por: `ClientHomeActivity`, `ProviderHomeActivity`,
  `ServicesActivity` (as 3 que já funcionavam, validadas ao vivo em rodadas anteriores).
- `StatusBarStripColor` — deixa a activity escolher a cor da faixa da barra de status
  no 15+: `PhotoEvidence`, `ProviderChat`, `DigitalSignature`, `Checklist`
  (header verde → `secondary_color`) e `ImagePreview` (visualizador → `black`).

### FloatingMicHelper
Listener movido do decorView para o próprio container do FAB + leitura via
`ViewCompat.getRootWindowInsets` (com `post` pós-attach, pois o tratador global consome
os insets antes de chegarem ao container). O inset da navegação só é somado quando a
janela é realmente edge-to-edge (`EdgeToEdgeInsets.isEdgeToEdge`).

## Arquivos alterados

- `app/src/main/res/values/themes.xml` — sem translucent, status bar laranja.
- `app/src/main/java/com/aquiresolve/app/EdgeToEdgeInsets.kt` — **novo** (tratador global + markers).
- `AppApplication.kt` — `EdgeToEdgeInsets.install(this)`.
- `InsetsHelper.kt` — **removido**.
- Hacks fullscreen removidos: `MainActivity`, `SignUpActivity`, `ClientSignUpActivity`,
  `ProviderSignUpActivity`, `ForgotPasswordActivity`, `RatingActivity`,
  `ClientRatingActivity`, `HomeActivity`, `ImagePreviewActivity`, `CreateOrderActivity`.
- Chamadas do InsetsHelper removidas: `HomeActivity`, `ClientOrdersActivity`,
  `AssistantChatActivity`, `OrderDetailsActivity`, `CreateOrderActivity`.
- Markers: `ClientHomeActivity`, `ProviderHomeActivity`, `ServicesActivity`
  (`InsetsSelfManaged`); `PhotoEvidenceActivity`, `ProviderChatActivity`,
  `DigitalSignatureActivity`, `ChecklistActivity`, `ImagePreviewActivity`
  (`StatusBarStripColor`).
- `FloatingMicHelper.kt` — fix do listener no decorView.

## Validação

- `./gradlew assembleDebug` + `./gradlew test` OK (JDK em
  `/usr/lib/jvm/java-17-openjdk-amd64` via `-Dorg.gradle.java.home`; o default da
  máquina é um JRE 21 sem compilador).
- **Waydroid (Android 11, regime antigo):** login (conteúdo abaixo da status bar, com
  `statusBarBackground` sólido), `ClientHomeActivity` (header laranja sob o relógio,
  bottom nav acima da barra de gestos, mic no lugar), `CreateOrderActivity` e
  `ClientOrdersActivity` (título abaixo da barra — antes ficava embaixo do relógio).
- Android 15+: verificado por construção (tratador global); não havia imagem 15+ no
  PC no momento do teste (AVD local é API 36, boot interrompido).

## Gotchas para não regredir

- **Nunca** reintroduzir `windowTranslucentStatus` nem o bloco
  `SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN` em activities.
- Tela nova que trate os próprios insets deve implementar `InsetsSelfManaged`,
  senão o tratador global soma padding em cima (15+).
- Nunca registrar `setOnApplyWindowInsetsListener` no **decorView**.
- `window.statusBarColor` em runtime continua OK para <15; no 15+ é ignorado — se a
  tela precisa de faixa de outra cor, implemente `StatusBarStripColor`.
