# Correção — Política de Permissões de Foto e Vídeo do Google Play (2026-07-06)

## O problema reportado pelo Google Play

> **Problema encontrado:** O uso da permissão não está diretamente relacionado à
> finalidade principal do seu aplicativo. Seu aplicativo não está em conformidade
> com a forma como as permissões **READ_MEDIA_IMAGES / READ_MEDIA_VIDEO** devem ser
> usadas. Seu aplicativo requer apenas acesso **único ou esporádico** a arquivos de
> mídia. Remova o uso da permissão de **todos os códigos de versão** (produção e
> teste) e use o **seletor de fotos do Android** (Android Photo Picker).

O AquiResolve só usa mídia de forma **pontual** (anexar uma foto do problema, um
documento de verificação, a foto de perfil etc.). Esse é exatamente o caso em que
o Google **não** permite `READ_MEDIA_IMAGES` — a política manda usar o **Android
Photo Picker** (`ActivityResultContracts.PickVisualMedia`), que **não exige
permissão nenhuma**.

## O que foi feito

Migração completa da seleção de imagens para o **Android Photo Picker** e remoção
de todas as permissões de leitura de mídia. **Nenhuma funcionalidade foi perdida**:
o `PickVisualMedia` devolve a mesma `Uri` que o `GetContent`/`ACTION_GET_CONTENT`
devolvia, então todo o resto do fluxo (recorte no UCrop, upload no Firebase Storage,
envio para a IA `/api/ai/vision`, etc.) continua idêntico. Em aparelhos antigos sem
o Photo Picker nativo, a biblioteca `androidx.activity` 1.8.1 faz o fallback
automático — **sem** exigir permissão.

### 1. `AndroidManifest.xml`

- **Removidas** `READ_MEDIA_IMAGES` e `READ_EXTERNAL_STORAGE`.
- Adicionadas remoções explícitas (`tools:node="remove"`) para
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` e `READ_EXTERNAL_STORAGE` — blindagem
  contra o **manifest merger**, caso alguma biblioteca (Glide, UCrop…) tente
  reinjetá-las. Verificado no manifest mesclado: sobra **só `CAMERA`** das
  permissões de captura/mídia.
- `CAMERA` foi **mantida** (o Google não reclamou dela e a captura por câmera
  continua existindo).

### 2. Helpers de permissão

- **`utils/PermissionHelper.kt`** — removidos `isMediaPermissionGranted()` e
  `getRequiredMediaPermission()`; `getAllRequiredPermissions()` agora devolve só
  `CAMERA`.
- **`utils/ImagePermissionHelper.kt`** — removidos `READ_MEDIA_IMAGES_PERMISSION`,
  `WRITE_EXTERNAL_STORAGE_PERMISSION`, `GALLERY_PERMISSIONS`, `ALL_PERMISSIONS`,
  `hasGalleryPermissions()`, `hasAllPermissions()`. Os managers
  (`ActivityPermissionManager` / `FragmentPermissionManager`) trocaram
  `checkAndRequestImagePermissions` / `checkAndRequestGalleryPermissions` por um
  único **`checkAndRequestCameraPermission`** (usado só no ramo "Tirar Foto").

### 3. Telas migradas para o Photo Picker

| Arquivo | Antes | Depois |
|---|---|---|
| `ImagePickerActivity.kt` | `GetContent("image/*")` + gate de mídia | `PickVisualMedia(ImageOnly)`, sem gate; câmera pede `CAMERA` |
| `ProfileActivity.kt` | diálogo gated por permissão de mídia | diálogo direto; galeria → Photo Picker; câmera → `CAMERA` |
| `ProviderProfileFragment.kt` | idem Profile | idem Profile |
| `CreateOrderActivity.kt` | `checkAndRequestGalleryPermissions` + `GetContent` | Photo Picker direto, sem permissão |
| `PhotoEvidenceActivity.kt` | `GetContent("image/*")` | `PickVisualMedia(ImageOnly)` |
| `AssistantChatActivity.kt` (Helô vê a foto) | `GetContent("image/*")` | `PickVisualMedia(ImageOnly)` |
| `RefundRequestActivity.kt` | `GetMultipleContents("image/*")` | `PickMultipleVisualMedia(5)` |
| `ProviderNichesActivity.kt` | `GetContent` × 2 + `GetMultipleContents` | `PickVisualMedia` × 2 + `PickMultipleVisualMedia(3)` |
| `DocumentUploadActivity.kt` | `ACTION_GET_CONTENT` + `isMediaPermissionGranted` | `PickVisualMedia(ImageOnly)`; removida toda a máquina de permissão de mídia |
| `ProviderDocumentUploadActivity.kt` | `startActivityForResult(ACTION_GET_CONTENT)` | launcher `PickVisualMedia(ImageOnly)` |

### 4. O que **não** mudou (de propósito)

- **Seletores de documento que aceitam PDF** — `ProviderProfileFragment`
  (`serviceDocsLauncher`, `"*/*"`) e o anexo de documentos dos chats
  (`ClientChatActivity` / `ProviderChatActivity`, `chatDocumentPickerLauncher`,
  `"*/*"`) continuam com `ActivityResultContracts.GetContent()` (SAF /
  `ACTION_OPEN_DOCUMENT`). Isso **nunca** exigiu as permissões de mídia e é a
  ferramenta correta para arquivos mistos (imagem **ou** PDF). Não infringe a
  política.
- **`ImagePreviewActivity`** usa `ACTION_SEND` (`type="image/*"`) para
  **compartilhar** uma imagem — não é seletor e não pede permissão.
- **`CAMERA`** e seu fluxo de captura permanecem intactos.

## Como cumprir a exigência no Play Console

O Google pede para **remover a permissão de todos os tracks** (produção **e** teste)
e reenviar para revisão:

1. Gerar um **novo AAB/APK** com estas mudanças (`./gradlew bundleRelease` ou o
   workflow `Build APK`). O `versionCode` novo já não declara as permissões de mídia.
2. Publicar esse build em **todos** os tracks ativos (produção, closed/open testing,
   internal) — a política verifica todos os `versionCode` da submissão.
3. Em **Política → Permissões de app** (ou na própria notificação), clicar em
   **"Enviar para revisão"** após o novo build estar disponível.

## Verificação feita

- `./gradlew :app:compileDebugKotlin` → **BUILD SUCCESSFUL** (sem erros).
- Manifest mesclado (`merged_manifest/debug`) inspecionado: das permissões de
  mídia/captura, **só resta `android.permission.CAMERA`**. Nenhuma
  `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_EXTERNAL_STORAGE` /
  `READ_MEDIA_VISUAL_USER_SELECTED`.

## Pendências / atenção

- **Requer novo APK/AAB nos aparelhos e nos tracks** — a mudança é de código.
- Se algum dia for necessário **acesso persistente** a toda a galeria (não é o caso
  hoje), aí sim seria preciso justificar a permissão de mídia via formulário de
  declaração no Play Console. Enquanto o uso for pontual, o Photo Picker é o caminho.
