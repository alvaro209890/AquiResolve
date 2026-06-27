# 11 — Som de Notificação de Mensagens + Microfone Flutuante da Helô

**Data:** 26/06/2026
**Commits:** (a definir)

## 1. Som de Notificação para Mensagens (FCM)

### Problema
- Notificações de mensagem (`provider_message` / `central_message`) mostravam "Nova notificação" genérico
- Som não tocava porque dependia do toggle global `notification_sound_enabled`
- Ao tocar na notificação, abria HomeActivity em vez da tela de chat

### Solução — `FirebaseMessagingService.kt`

| Correção | Detalhe |
|---|---|
| **Merge notification + data** | `title`/`body` agora vêm do `remoteMessage.notification` (backend manda title/body no campo `notification`, não no `data`) |
| **Canal dedicado `messages_channel`** | `IMPORTANCE_HIGH` garante som mesmo com toggle global desligado |
| **Tipos reconhecidos** | `provider_message`, `central_message` e `chat` passam pelo filtro `chat_notifications_enabled` |
| **Navegação correta** | `central_message` → `ClientCentralChatActivity`; `provider_message` → `HomeActivity` com extra `open_provider_chat=true` → `ClientCentralChatActivity` |
| **IDs únicos** | `System.currentTimeMillis().toInt()` evita agrupamento de notificações consecutivas |

### Arquivos alterados
- `app/.../FirebaseMessagingService.kt` — canal dedicado, merge payloads, navegação por tipo
- `app/.../HomeActivity.kt` — `checkChatNotification()` trata `open_provider_chat` extra

---

## 2. Microfone Flutuante da Helô

### Funcionalidade
Botão flutuante (FAB) com ícone de microfone posicionado no canto **inferior-esquerdo**.
Visível em todas as telas do cliente (exceto no próprio chat da Helô).

### Comportamento
1. Toque no FAB → verifica permissão `RECORD_AUDIO`
2. Permissão OK → abre `AssistantChatActivity` com `start_with_voice=true`
3. Helô abre com microfone já ativo (após 400ms delay para UI estabilizar)
4. Animação pulse sutil (scale 1.0 → 1.08) para destaque visual
5. Margem inferior ajusta automaticamente com altura da barra de navegação do sistema

### Arquivo novo
- `app/.../FloatingMicHelper.kt` — helper reutilizável com `attach(activity)` / `detach()`

### Activities modificadas (9 telas)
| Activity | Uso |
|---|---|
| `ClientHomeActivity` | Home principal do cliente |
| `HomeActivity` | Home legada do cliente |
| `ServicesActivity` | Lista de serviços |
| `ClientOrdersActivity` | Pedidos do cliente |
| `ClientCartActivity` | Carrinho |
| `CreateOrderActivity` | Criar pedido |
| `OrderDetailsActivity` | Detalhes do pedido |
| `CashbackActivity` | Cashback |
| `ProfileActivity` | Perfil (compartilhado) |

### AssistantChatActivity
- Trata `intent.getBooleanExtra("start_with_voice", false)` no `onCreate`
- `startVoiceIfPermitted()` → verifica permissão → `startVoice()` ou `micPermissionLauncher.launch()`

### Padrão de uso por activity (3 linhas)
```kotlin
private val floatingMic = FloatingMicHelper()
// no onCreate: floatingMic.attach(this)
// no onDestroy: floatingMic.detach()
```

---

## Verificação
- 12 arquivos Kotlin modificados/criados — todos com braces balanceados
- 9 activities cliente com pattern field/attach/detach completo
- Nenhum build error estrutural detectado
