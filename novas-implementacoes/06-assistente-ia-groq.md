# 06 — Assistente AquiResolve (IA via Groq / Llama 70B)

**Prioridade:** 🔵 Baixa (último da leva) · **Fase:** 3 · **Complexidade:** Média-Alta

---

## 🎯 Objetivo

Botão **🤖 Assistente AquiResolve**: o cliente descreve em linguagem natural o que aconteceu
("minha pia está vazando", "a luz da sala não acende") e a IA **identifica o nicho** (elétrica,
hidráulica, limpeza, refrigeração…) e **direciona** direto para o fluxo de pedido daquele nicho.

> A IA é **conveniência**, não dependência: a busca (plano `05`) e as categorias (plano `02`)
> já resolvem a maioria dos casos. O assistente é o atalho para quem não sabe o nome do serviço.

---

## 🧩 Contexto atual

- **Provedor:** Groq, modelo Llama 70B → usar **`llama-3.3-70b-versatile`** (Llama 70B atual da Groq;
  confirmar o id vigente no console da Groq na hora de implementar, pois muda de tempos em tempos).
- A API da Groq é **compatível com o formato OpenAI** (`POST /openai/v1/chat/completions`),
  então dá para usar Retrofit (já no projeto) com um endpoint simples.
- **Nichos disponíveis** vêm de `CatalogRepository.cachedNicheNames()` — a IA deve classificar
  **dentro dessa lista**, não inventar categorias.
- **Precedente de proxy no backend:** o mini-mapa do prestador usa um proxy `/api/route` no backend
  Render para falar com o OSRM (ver memória "mini-mapa prestador"). **Mesmo padrão aqui.**

---

## 🔐 Decisão de segurança (CRÍTICA) — a chave Groq NÃO vai no APK

Embutir a chave da Groq no app permite que qualquer um a extraia do APK e gaste o saldo. Portanto:

- ✅ **Criar um endpoint proxy no backend Render** (`backend/`), ex.: `POST /api/ai/classify`.
  O app chama esse endpoint; o backend guarda `GROQ_API_KEY` (env var no Render) e fala com a Groq.
- ✅ O backend valida o usuário (idealmente verificar o ID token do Firebase, como já é feito em
  outras rotas) e aplica rate-limit (o backend já usa `express-rate-limit`).
- ⛔ **Nunca** colocar `GROQ_API_KEY` em `app/build.gradle`/`BuildConfig`.

Fluxo:
```
[App] --POST /api/ai/classify {descricao, niches[]}--> [Backend Render] --Groq chat/completions--> [Groq]
                                              <-- {niche, confidence, message} --
```

---

## ✅ Escopo

**Entra:**
- ✅ Endpoint `POST /api/ai/classify` no backend (proxy Groq, com a lista de nichos válida).
- ✅ Tela/bottom-sheet do assistente: campo "O que aconteceu?", resposta com nicho sugerido + CTA.
- ✅ Classificação restrita aos nichos do catálogo; retorno em JSON estruturado.
- ✅ Fallback: se a IA falhar/baixa confiança → cair na busca (plano `05`) ou "Ver todos os serviços".
- ✅ Botão de acesso na Home + gancho no estado "sem resultado" da busca.

**Não entra (⛔):**
- ⛔ Conversa multi-turno / chat completo (nesta entrega é 1 pergunta → 1 direcionamento).
- ⛔ Voz (STT/TTS).
- ⛔ Estimativa de preço por IA (o preço continua vindo do `catalog_services`/backend).

---

## 🗄 Modelo de dados

**Nenhuma coleção obrigatória.** (Opcional/futuro: logar interações em `ai_assistant_logs`
via Admin SDK no backend, para melhorar prompts — fora do escopo desta entrega.)

---

## 🛠 Backend (`backend/` — Node/Express)

### Endpoint `POST /api/ai/classify`
- **Env var:** `GROQ_API_KEY` (configurar no Render; ver skill `aquiresolve-render`).
- **Body:** `{ "description": "minha pia está vazando", "niches": ["Elétrica","Encanador",...] }`
  - `niches` enviado pelo app (catálogo atual) para a IA classificar **dentro da lista**.
- **Chamada Groq** (`https://api.groq.com/openai/v1/chat/completions`):
  - `model: "llama-3.3-70b-versatile"`
  - `temperature: 0` (classificação determinística)
  - `response_format: { type: "json_object" }` (saída JSON)
  - **system prompt** (resumo): "Você é o assistente da AquiResolve. Dada a descrição do problema do
    cliente, escolha **um** nicho da lista fornecida. Responda só em JSON:
    `{\"niche\": <um da lista ou null>, \"confidence\": 0..1, \"message\": <frase curta e amigável>}`.
    Se nada se encaixar, `niche=null`."
- **Resposta ao app:** `{ "niche": "Encanador", "confidence": 0.93, "message": "Parece um problema hidráulico. Posso te levar para Encanador?" }`
- **Robustez:** timeout, try/catch que nunca derruba o fluxo, validar que `niche` ∈ `niches`
  (se a IA "alucinar" um nicho fora da lista → tratar como `null`).
- **Segurança:** verificar ID token do Firebase (header `Authorization: Bearer`), rate-limit por usuário.

### Variáveis no Render
- [ ] `GROQ_API_KEY` (nova).
- Lembrar: backend tem **autoDeploy OFF** → deploy manual após adicionar a rota.

---

## 📱 App (camadas)

### 1. API service (Retrofit)
- Adicionar método no service de rede (ou um novo `AiApiService.kt`) apontando para
  `BuildConfig.PAYMENTS_API_BASE_URL` base (o backend é o mesmo host Render) — endpoint `ai/classify`.
- Reusar o cliente OkHttp/Retrofit já configurado.

### 2. UI do assistente
- `AssistantActivity` **ou** bottom-sheet `AssistantBottomSheet`:
  - Campo de texto "O que aconteceu?" + botão enviar.
  - Estado: carregando → resposta (mensagem amigável + nicho sugerido) → CTA "Sim, continuar"
    (vai ao fluxo de pedido no nicho) / "Não é isso" (abre busca / lista completa).
- Layout: `activity_assistant.xml` / `sheet_assistant.xml`.

### 3. Acesso
- Botão "🤖 Assistente AquiResolve" na Home (card ou FAB) — `ClientHomeActivity`.
- Gancho: estado "sem resultado" da busca (plano `05`) → abre o assistente com a query já preenchida.

### 4. Fluxo
1. Usuário descreve → app envia `{description, niches: CatalogRepository.cachedNicheNames()}`.
2. Backend classifica via Groq → retorna nicho/confiança/mensagem.
3. App mostra a sugestão. Se `confidence` baixa ou `niche=null` → oferecer busca/lista.
4. "Continuar" → fluxo de criação de pedido no nicho (mesma chave de extra das categorias).
- Analytics: `ia_assistente_open`, `ia_nicho_sugerido` (niche/confidence), `ia_sugestao_aceita`.

---

## 🎨 Design / UX

- Tom amigável e curto (a `message` da IA já vem assim). Mostrar o nicho como "chip" clicável.
- Deixar claro que é uma sugestão ("Acho que é..."), com saída fácil para busca manual.
- Microcopy de fallback: "Não consegui identificar agora — quer ver todos os serviços?".
- Indicador de carregamento discreto (a chamada leva ~1–2s).

---

## ✔️ Checklist

### Backend (Render)
- [ ] Implementar `POST /api/ai/classify` (proxy Groq, JSON estruturado, restrição à lista de nichos).
- [ ] Verificação de ID token Firebase + rate-limit.
- [ ] Configurar `GROQ_API_KEY` no Render.
- [ ] Deploy manual do backend (autoDeploy off).
- [ ] Testar via curl (descrições variadas → nicho correto / null quando não encaixa).

### App
- [ ] `AiApiService.kt` (Retrofit) apontando ao backend.
- [ ] UI do assistente (Activity ou bottom-sheet) + layout.
- [ ] Botão "🤖 Assistente" na Home.
- [ ] Gancho no estado "sem resultado" da busca.
- [ ] Enviar `niches` do `CatalogRepository`; validar nicho retornado contra o catálogo.
- [ ] Roteamento "Continuar" → criação de pedido no nicho.
- [ ] Fallbacks (erro de rede, confiança baixa, niche null).
- [ ] Eventos Analytics da IA.

### Segurança / QA
- [ ] **Confirmar que NÃO há `GROQ_API_KEY` no app/BuildConfig.**
- [ ] Descrições comuns classificam certo (vazamento→Encanador, luz→Elétrica, faxina→Limpeza, ar→Ar condicionado).
- [ ] Descrição sem sentido → `niche=null` → fallback amigável.
- [ ] Offline/erro do backend → app não trava, oferece busca/lista.
- [ ] Latência aceitável (< ~3s) com loading visível.

---

## 🟢 Critérios de aceite

1. Cliente descreve o problema e recebe um nicho coerente + atalho para o pedido.
2. A chave Groq vive só no backend (auditado no APK).
3. Falha da IA nunca bloqueia a contratação — sempre há caminho manual.

---

## ⚠️ Riscos & gotchas

- **Id do modelo muda:** confirmar o nome do Llama 70B vigente na Groq ao implementar
  (`llama-3.3-70b-versatile` é o atual; pode haver sucessor). Manter o id como **env var** no backend
  para trocar sem deploy do app.
- **Alucinação de nicho:** sempre validar `niche ∈ niches` no backend e no app.
- **Custo/abuso:** rate-limit no backend (já há `express-rate-limit`) + auth por ID token.
- **Idioma:** instruir o prompt a responder em **pt-BR**.
- **`response_format json_object`** exige instrução explícita de JSON no prompt (a Groq segue o
  formato OpenAI; testar que volta JSON válido e fazer parse defensivo).

---

## 📦 Estimativa

Média-Alta — ~1 dia (backend/proxy) + ~1,5 a 2 dias (app/UX). Depende de `02`/`05` para os ganchos.
</content>
