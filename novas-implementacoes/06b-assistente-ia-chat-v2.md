# 06b — Assistente IA Chat Multi-turno v2 (Streaming + Sugestões)

**Prioridade:** 🔵 Atualização · **Data:** 2026-06-24 · **Base:** plano 06 original (commit `4d93917`)

---

## ✅ O que mudou

O assistente IA foi atualizado de single-turn (1 pergunta → 1 sugestão de nicho) para:

1. **Chat multi-turno** — conversa completa com histórico. A IA lembra do contexto das mensagens anteriores.
2. **Streaming SSE** — resposta aparece token por token (como ChatGPT), sem esperar 2-3s.
3. **Sugestões rápidas** — 6 chips clicáveis na abertura: "🪠 Estou com um vazamento", "🔌 Problema elétrico", etc.

## ✅ Arquitetura

```
[AssistantChatActivity] ──SSE streaming──▶ [POST /api/ai/chat] ──Groq stream──▶ [Llama 70B]
        │                                         │
   Chat bubbles                            Backend Render
   RecyclerView                            text/event-stream
   Multi-viewtype                          token por token
```

## ✅ Arquivos novos

| Camada | Arquivo | Função |
|---|---|---|
| Backend — service | `backend/src/services/ai-chat.service.js` | Streaming Groq com system prompt multi-turno |
| Backend — route | `backend/src/routes/ai-chat.routes.js` | `POST /api/ai/chat` com SSE (`text/event-stream`) |
| Backend — registro | `backend/src/app.js` | Mount da rota com `aiLimiter` |
| App — cliente | `app/.../AssistantChatClient.kt` | OkHttp streaming SSE, callbacks onToken/onDone/onError |
| App — activity | `app/.../AssistantChatActivity.kt` | Chat UI completa com RecyclerView multi-type |
| App — layout | `app/.../layout/activity_assistant_chat.xml` | Layout principal do chat |
| App — layout | `app/.../layout/item_chat_message_user.xml` | Bubble do usuário (laranja, direita) |
| App — layout | `app/.../layout/item_chat_message_assistant.xml` | Bubble do assistente (branco, esquerda) |
| App — drawable | `app/.../drawable/bg_send_button.xml` | Fundo do botão enviar |
| App — values | `app/.../values/dimens.xml` | chip_corner_radius, chip_spacing |

## ✅ Arquivos modificados

| Arquivo | Mudança |
|---|---|
| `app/.../AndroidManifest.xml` | Registro da `AssistantChatActivity` |
| `app/.../ClientHomeActivity.kt` | Card do assistente + gancho de busca → `AssistantChatActivity` |

## ✅ Compatibilidade

- `AssistantActivity` e `AssistantClient` antigos **permanecem intactos** (zero regressão)
- Endpoint `/api/ai/classify` antigo **continua ativo** (painel admin ainda usa via copiloto IA)
- Navegação atualizada: Home → card assistente → novo chat / busca sem resultado → novo chat

## 🔐 Segurança

- Chave Groq **continua só no backend** (`GROQ_API_KEY` no Render)
- Novo endpoint usa `authenticateRequest` (Firebase ID token)
- Mesmo rate-limit `aiLimiter` (15/min/IP)
- Timeout de 45s no stream (segurança contra conexões penduradas)

## 🚀 Deploy

Após merge, fazer deploy manual do backend no Render:
```bash
curl -X POST "https://api.render.com/v1/services/srv-d6hmk2p4tr6s73bu5fm0/deploys" \
  -H "Authorization: Bearer $(cat /home/acer/Documentos/Aqui_Resolve/.render-credentials)" \
  -H "Content-Type: application/json"
```

## 🧪 Teste

```bash
# Build
cd /home/acer/Documentos/Aqui_Resolve && ./gradlew assembleDebug

# Backend local (precisa GROQ_API_KEY no .env)
cd backend && npm start

# SSE test
curl -N -X POST http://localhost:3000/api/ai/chat \
  -H "Authorization: Bearer <firebase-id-token>" \
  -H "Content-Type: application/json" \
  -d '{"messages":[{"role":"user","content":"minha pia esta vazando"}],
       "niches":["Encanador","Elétrica","Limpeza","Ar Condicionado"]}'
```
