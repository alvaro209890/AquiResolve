# 11-chat-ia-historico-persistencia.md

**Data:** 2026-06-26
**Solicitante:** Álvaro (WhatsApp)
**Branch:** main
**Commit:** [a ser preenchido]

## O que foi feito

### 1. Diagnóstico inteligente da IA

A IA (Hello) agora funciona como um assistente de diagnóstico:

- Quando o cliente descreve um problema, a IA faz perguntas para entender melhor: o que, onde, há quanto tempo, urgência
- Quando identifica o serviço certo, adiciona uma tag `[NICHE:Nome]` que é parseada pelo backend
- O app mostra um botão "Solicitar [Serviço]" que abre direto o `CreateOrderActivity`

**Exemplo de conversa:**
```
Cliente: "To com um vazamento"
IA: "Poxa! Onde é o vazamento? Na pia, no teto, no banheiro?"
Cliente: "Na pia da cozinha"
IA: "Entendi, parece serviço de encanador. Quer que eu te direcione? [NICHE:Encanador]"
→ Botão "Solicitar Encanador" aparece no balão
```

### 2. Histórico de chats

- Cada conversa é salva no Firestore (`ai_chats/{chatId}/messages`)
- Botão "Ver chats" (ícone de lista) no header do chat da IA
- Lista de conversas anteriores com título, última mensagem e data relativa
- Ao abrir pelo Hello da navbar → **sempre chat novo**
- Ao abrir pelo histórico → carrega conversa existente, pode continuar

### 3. Estrutura Firestore

```
ai_chats/{chatId}
  - userId: string
  - title: string (primeira mensagem do usuário)
  - lastMessage: string
  - niche: string? (último nicho sugerido)
  - createdAt: timestamp
  - updatedAt: timestamp

ai_chats/{chatId}/messages/{msgId}
  - role: "user" | "assistant"
  - content: string
  - timestamp: timestamp
  - niche: string? (apenas mensagens da IA com sugestão)
```

### 4. Fluxo de navegação

```
BottomNav "Hello" → AssistantChatActivity (NOVO chat)
    ├── btnHistory → AssistantChatListActivity (lista de chats)
    │       ├── tap chat → AssistantChatActivity (carrega histórico)
    │       └── "+ Novo" → AssistantChatActivity (novo chat)
    ├── digita → IA responde → botão "Solicitar"
    └── btnBack → volta pra Home
```

### 5. Arquivos alterados/criados

| Arquivo | Alteração |
|---------|-----------|
| `backend/src/services/ai-chat.service.js` | System prompt v3 com fluxo de diagnóstico |
| `backend/src/routes/ai-chat.routes.js` | Parse da tag `[NICHE:...]`, envia `niche` no done |
| `app/.../AssistantChatActivity.kt` | Persistência Firestore, chatId, carregar histórico |
| `app/.../AssistantChatListActivity.kt` | **NOVO** — lista de chats anteriores |
| `app/.../AssistantChatClient.kt` | Callback `onDone` com `suggestedNiche` |
| `app/.../res/layout/activity_assistant_chat.xml` | Botão btnHistory no header |
| `app/.../res/layout/activity_assistant_chat_list.xml` | **NOVO** — layout da lista |
| `app/.../res/layout/item_chat_history.xml` | **NOVO** — item da lista |
| `app/.../res/layout/item_chat_message_assistant.xml` | Botão "Solicitar [Nicho]" |
| `app/.../AndroidManifest.xml` | Registro da AssistantChatListActivity |
| `firestore.rules` | Regras para `ai_chats` e subcoleção `messages` |
| `dashboard_admin/firestore.rules` | Sincronizado |
| `infra-config/firebase/firestore.rules` | Sincronizado |

### 6. Deploy

- **Firebase Rules:** publicado (`firebase deploy --only firestore:rules`)
- **Render:** backend deployado com novo system prompt e parse de niche
- **Vercel:** sem alterações no dashboard_admin neste commit
