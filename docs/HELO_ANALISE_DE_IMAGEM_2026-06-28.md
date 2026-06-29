# Nova funcionalidade: Helô analisa imagem e sugere o serviço — 2026-06-28

O cliente envia uma **foto do problema** para a Helô (assistente IA do app) e ela
identifica, em linguagem natural, **qual serviço/nicho** ele precisa — usando um
**modelo de visão da Groq**. Reaproveita o fluxo existente: a resposta traz o botão
"Solicitar <nicho>" que abre a criação de pedido já no nicho certo.

## Como funciona
1. Na tela da Helô (`AssistantChatActivity`), um novo botão de **câmera** abre o
   seletor "Tirar foto / Escolher da galeria".
2. O app **reduz** a imagem (≤1024px, JPEG 75%), converte para base64 e envia para
   `POST /api/ai/vision` (autenticado com Firebase ID token). A imagem **não é
   persistida** no servidor.
3. O backend chama a Groq (modelo multimodal) com um prompt **ancorado nos nichos do
   catálogo** e devolve `{ text, niche, nicheSlug }`.
4. A Helô mostra a análise; se houver nicho, aparece o botão **"Solicitar <nicho>"**
   (mesmo fluxo do chat de texto → `CreateOrderActivity` com `service_category_name`).

## Backend
- **Serviço** `src/services/ai-vision.service.js` (modular/testável):
  `buildVisionSystemPrompt`, `parseVisionResult` (extrai `[NICHE:...]` + slug),
  `toImageDataUrl`, `analyzeImage` (chamada Groq).
- **Rota** `POST /api/ai/vision` em `ai-chat.routes.js` (mesmo `aiLimiter` + auth):
  valida imagem/nichos, limita tamanho, trata erro (502/503) sem derrubar o servidor.
- **Modelo** configurável por env `GROQ_VISION_MODEL`
  (default `meta-llama/llama-4-scout-17b-16e-instruct` — multimodal, disponível na
  conta Groq). Usa a **mesma `GROQ_API_KEY`** já existente — **nenhum env novo
  obrigatório**.

## App
- `AssistantVisionClient.kt` — POST não-streaming para `/api/ai/vision`.
- `AssistantChatActivity` — botão `btnAttachImage`, captura (câmera via FileProvider
  no cache) / galeria (`GetContent`), downscale+JPEG+base64 em IO, bolha "📷 Foto
  enviada", resposta da Helô com CTA de nicho. Eventos: `ia_imagem_enviada`,
  `ia_imagem_niche`.
- Permissão `CAMERA` já existia no Manifest. **Exige APK novo** (é código).

## Validação
- **Backend unit** (`node --test`): **11/11** (4 de visão: prompt ancorado, parse de
  `[NICHE:]`+slug, sem-tag→null, dataURL; + 7 do som).
- **Live end-to-end** (`analyzeImage` real contra a Groq, cena sintética com tomada +
  fios expostos): resposta *"problema elétrico… serviço de elétrica"* →
  **`NICHE: Elétrica | slug: eletrica`**. Modelo de visão confirmado disponível na
  conta (lista de modelos) e lendo imagem (teste de cor → "Vermelho").
- **App**: `compileDebugKotlin` + `testDebugUnitTest` + `assembleDebug` **SUCCESSFUL**.

## Publicação
| Alvo | Mudou? | Ação |
|---|---|---|
| **Render** (backend — rota/serviço de visão) | Sim | deploy manual |
| **GitHub** (Delta `main` + alvaro) | Sim | push |
| **Firebase / Vercel** | Não | nada a publicar |
| **APK debug** | Sim | gerado (UI é código) |
| **Env Render** | Opcional | `GROQ_VISION_MODEL` só se quiser trocar o modelo |
