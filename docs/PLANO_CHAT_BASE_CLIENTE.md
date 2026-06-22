# Plano — Chat Base ↔ Cliente (v1)

> Item #4 do escopo AquiResolve. Última peça que falta do scope original.
> Base↔Prestador já existe (`/dashboard/controle/chat-operacional`); Cliente↔Prestador
> já existe (5min após atribuição). Falta o canal Base↔Cliente:
> promoções, avisos e atualizações de serviços, **com chat bidirecional e histórico**.

---

## 1. Modelo de dados

### Coleção `client_chats/{clientId}` (1 doc por cliente)

| Campo | Tipo | Descrição |
|---|---|---|
| `clientId` | string | uid do cliente (== id do doc) |
| `clientName` | string | nome exibido na lista do painel |
| `clientEmail` | string | usado pra busca rápida |
| `lastMessage` | string | preview da última msg (truncado) |
| `lastMessageAt` | timestamp | ordena a lista do painel |
| `lastSender` | `'admin' \| 'client'` | quem mandou por último |
| `unreadByClient` | number | counter — incrementa quando admin envia, zera quando cliente abre |
| `unreadByAdmin` | number | counter — incrementa quando cliente envia, zera quando admin abre |
| `pinned` | boolean | painel pode fixar conversas importantes |
| `archived` | boolean | esconde do painel sem deletar |
| `createdAt` / `updatedAt` | timestamp | metadados |

### Subcoleção `client_chats/{clientId}/messages/{messageId}`

| Campo | Tipo | Descrição |
|---|---|---|
| `text` | string | conteúdo (até 2000 chars) |
| `senderType` | `'admin' \| 'client'` | |
| `senderId` | string | uid do remetente (admin: uid do operador) |
| `senderName` | string | nome exibido na bolha |
| `type` | `'text' \| 'promotion' \| 'notice' \| 'order_update'` | tag visual diferente no app |
| `relatedOrderId` | string? | só preenchido quando `type='order_update'` |
| `attachmentUrl` | string? | **v2** — anexos ficam pra próxima |
| `createdAt` | timestamp | server-time |
| `readByClient` | boolean | |
| `readByAdmin` | boolean | |
| `broadcastId` | string? | preenchido quando veio de broadcast (rastreabilidade) |

### Coleção auxiliar `client_chat_broadcasts/{broadcastId}` (opcional v1, recomendado v2)

Snapshot de cada disparo em massa pra auditoria: `text`, `type`, `audience`,
`audienceCount`, `sentByAdminId`, `createdAt`. Não é lido pelo app — só pelo painel.

---

## 2. Firestore rules (delta a aplicar em `firestore.rules`)

```javascript
match /client_chats/{clientId} {
  allow read: if isOwner(clientId);
  allow write: if false;  // metadata só via Admin SDK

  match /messages/{messageId} {
    allow read: if isOwner(clientId);
    allow create: if isOwner(clientId)
      && request.resource.data.senderType == 'client'
      && request.resource.data.senderId == request.auth.uid
      && request.resource.data.text is string
      && request.resource.data.text.size() <= 2000
      && request.resource.data.keys().hasOnly(
           ['text','senderType','senderId','senderName','type','createdAt','readByClient','readByAdmin']
         );
    allow update, delete: if false;  // só Admin SDK
  }
}

match /client_chat_broadcasts/{broadcastId} {
  allow read, write: if false;  // só Admin SDK
}
```

**Por que `unreadByAdmin/unreadByClient` é só Admin SDK:** evita cliente
manipular o contador. Cliente envia mensagem → API Route do app NÃO existe;
em vez disso, o cliente cria o doc em `messages/` e o painel **detecta via
snapshot listener** (admin) e atualiza o `unreadByAdmin` por trigger ou
on-read. Alternativa pragmática v1: o app **não escreve** metadata, só cria
mensagem; o painel atualiza metadata ao receber. Quando o admin envia, ele
atualiza metadata na mesma chamada Admin SDK.

---

## 3. Índices `firestore.indexes.json` (delta)

```json
{
  "collectionGroup": "messages",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "createdAt", "order": "DESCENDING" }
  ]
}
```

E para a lista de conversas no painel:

```json
{
  "collectionGroup": "client_chats",
  "queryScope": "COLLECTION",
  "fields": [
    { "fieldPath": "archived", "order": "ASCENDING" },
    { "fieldPath": "pinned",   "order": "DESCENDING" },
    { "fieldPath": "lastMessageAt", "order": "DESCENDING" }
  ]
}
```

---

## 4. App Android

### Arquivos novos
- `app/src/main/java/com/aquiresolve/app/ClientCentralChatActivity.kt`
  - Listener Firestore em `client_chats/{uid}/messages` ordenado por `createdAt ASC`
  - Botão "Enviar" cria doc com `senderType='client'`, `senderId=uid`
  - Ao abrir: zera `unreadByClient` chamando `PATCH /api/client-chats/{uid}/read` (Admin SDK)
- `app/src/main/java/com/aquiresolve/app/ClientCentralChatRepository.kt`
  - Wrapper de leitura/escrita; expõe `Flow<List<Message>>`
- `app/src/main/java/com/aquiresolve/app/adapters/ClientCentralChatAdapter.kt`
  - 2 view types (admin = balão à esquerda, client = balão à direita)
  - Tag visual por `type` (promoção = amarelo, aviso = âmbar, order_update = azul com link pro pedido)
- `app/src/main/res/layout/activity_client_central_chat.xml`
- `app/src/main/res/layout/item_chat_msg_admin.xml`
- `app/src/main/res/layout/item_chat_msg_client.xml`

### Arquivos a modificar
- `ClientHomeActivity.kt` + `activity_client_home.xml`
  - Botão "Mensagens da Central" com badge unread (já tem `NotificationBadgeHelper`,
    estender para também observar `client_chats/{uid}.unreadByClient`)
- `MyFirebaseMessagingService.kt` (ou equivalente)
  - Tratar payload com `type=central_message`: navega pra `ClientCentralChatActivity`

### Comportamento offline
- Snapshot listener já cobre cache. Mensagens enviadas em offline ficam
  pendentes até reconexão (Firestore SDK trata isso).

---

## 5. Painel admin (Next.js)

### Páginas
- `dashboard_admin/app/dashboard/controle/chat-clientes/page.tsx`
  - Layout espelho de `chat-operacional/page.tsx`: sidebar = lista de clientes
    com unread badge, painel = conversa do selecionado, footer = input + botão
    "Broadcast"
- Componente `dashboard_admin/components/chat-clientes/broadcast-dialog.tsx`
  - Modal pra enviar em massa: text, type, audience selector
    (`all` / `active` / `specific UIDs`)

### Sidebar
- `components/layout/sidebar.tsx` → adicionar item:
  ```
  { name: "Chat Clientes", href: "/dashboard/controle/chat-clientes", icon: MessageSquare }
  ```

### API Routes (todas Admin SDK)
| Rota | Método | Finalidade |
|---|---|---|
| `/api/client-chats` | GET | Lista chats (filtros `status=active\|archived`, `unreadOnly=true`, paginação) |
| `/api/client-chats/[clientId]/messages` | GET | Paginated, ordem desc |
| `/api/client-chats/[clientId]/messages` | POST | Admin envia. Body `{ text, type, relatedOrderId? }`. Cria msg + atualiza metadata + dispara FCM |
| `/api/client-chats/[clientId]/read` | PATCH | Marca como lido pelo admin (zera `unreadByAdmin`) |
| `/api/client-chats/broadcast` | POST | Body `{ text, type, audience, userIds? }`. Lista uids elegíveis, batch-write em chunks de 500, grava `client_chat_broadcasts/{id}` |
| `/api/client-chats/[clientId]` | PATCH | Atualiza `pinned`/`archived` |

### Lógica de broadcast
```typescript
const audienceUids = await resolveAudience(audience, userIds)
const broadcastRef = db.collection('client_chat_broadcasts').doc()
const broadcastId = broadcastRef.id
await broadcastRef.set({ text, type, audience, audienceCount: audienceUids.length, sentByAdminId, createdAt })

for (const chunk of chunkArray(audienceUids, 500)) {
  const batch = db.batch()
  for (const uid of chunk) {
    const chatRef = db.collection('client_chats').doc(uid)
    const msgRef = chatRef.collection('messages').doc()
    batch.set(msgRef, { text, type, senderType: 'admin', ..., broadcastId, createdAt: serverTimestamp() })
    batch.set(chatRef, { lastMessage: text, lastMessageAt: serverTimestamp(), lastSender: 'admin', unreadByClient: FieldValue.increment(1), updatedAt: serverTimestamp() }, { merge: true })
  }
  await batch.commit()
}

// FCM (em paralelo, não bloqueia)
await sendFCM({ tokens: audienceTokens, title: '...', body: text, data: { type: 'central_message' } })
```

---

## 6. Atualização da Central de Notificações existente

A página `/dashboard/controle/notificacoes` continua existindo para FCM
unidirecional (sino). O chat é complementar: avisos críticos vão pelos **dois**
canais. Não consolidar agora — mais simples manter separado.

---

## 7. Faseamento

### v1 (próxima leva — escopo desta entrega)
- [x] Modelo de dados + rules + índices
- [x] App: tela do cliente + botão home + badge
- [x] Painel: página de chat por cliente + broadcast simples (audience `all`)
- [x] FCM ao receber mensagem do admin
- [x] Doc em `CLAUDE.md`

### v2 (futuro)
- Anexos (imagens via Storage, reaproveitar regras do chat operacional)
- Templates reutilizáveis (`/dashboard/controle/chat-clientes/templates`)
- Segmentação avançada (cidade, prestador, último pedido)
- Marcar mensagem como importante / pinned no app
- Replay de broadcast (histórico em `client_chat_broadcasts`)

---

## 8. Riscos / decisões pendentes

| Risco | Mitigação |
|---|---|
| Custo de broadcast (10k clientes = 20k writes — msg + metadata) | v1 limita audience a "active" (login < 90d). v2 considera coleção compartilhada `broadcasts/{id}` com `readBy[]` |
| Cliente offline perde FCM mas tem doc Firestore | Snapshot listener no startup reconcilia — sino fica com unread correto |
| Spam de cliente → admin | Throttle no app (1 msg / 10s) + sanitize no painel. Se virar problema, mover envio pro lado do servidor (Admin SDK) e perder write client-side |
| Conflitos com Central de Notificações | Documentar claramente: notificação = unilateral, chat = bidirecional |

---

## 9. Checklist de execução (next session)

1. [ ] Atualizar `firestore.rules` (bloco `client_chats` + `client_chat_broadcasts`)
2. [ ] Atualizar `firestore.indexes.json`
3. [ ] Criar API routes em `dashboard_admin/app/api/client-chats/`
4. [ ] Criar página `dashboard_admin/app/dashboard/controle/chat-clientes/page.tsx`
5. [ ] Adicionar link em `components/layout/sidebar.tsx`
6. [ ] Criar `ClientCentralChatActivity` + layouts + adapter + repository
7. [ ] Adicionar botão "Mensagens da Central" em `ClientHomeActivity`
8. [ ] Estender `NotificationBadgeHelper` pra observar `unreadByClient`
9. [ ] Atualizar `MyFirebaseMessagingService` pra tratar `type=central_message`
10. [ ] Documentar em `CLAUDE.md` (tabela de coleções + API routes + páginas)
11. [ ] Commit + push ao `main`
12. [ ] **Outro PC**: `firebase deploy --only firestore:rules,firestore:indexes` + `vercel deploy --prod` + `./gradlew assembleDebug`

---

## 10. Estimativa

- App Android: ~6h (tela + adapter + repository + integração home + FCM)
- Painel admin: ~6h (página + 5 API routes + componente broadcast)
- Regras + índices + doc: ~1h
- **Total**: 1 dia de trabalho focado
