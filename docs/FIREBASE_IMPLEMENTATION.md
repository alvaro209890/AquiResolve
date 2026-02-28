# Implementação do Firebase no AppServiço

## Visão Geral

O Firebase foi completamente integrado ao aplicativo AppServiço, substituindo o sistema de autenticação local e adicionando funcionalidades em tempo real. Esta implementação inclui:

- **Firebase Authentication**: Autenticação de usuários
- **Firestore**: Banco de dados em tempo real
- **Firebase Storage**: Armazenamento de imagens e documentos
- **Firebase Cloud Messaging**: Notificações push
- **Firebase Analytics**: Análise de uso do aplicativo

## Configuração

### 1. Credenciais do Firebase

As credenciais do Firebase foram configuradas nos seguintes arquivos:

- `app/google-services.json`: Configuração principal do Firebase
- `app/src/main/res/values/firebase_config.xml`: Credenciais em formato XML
- `build.gradle` (projeto): Plugin do Google Services
- `app/build.gradle`: Dependências do Firebase

### 2. Permissões

As seguintes permissões foram adicionadas ao `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

## Componentes Implementados

### 1. FirebaseConfig

Classe singleton para inicialização e acesso aos serviços do Firebase:

```kotlin
// Inicializar Firebase
FirebaseConfig.initialize(context)

// Acessar serviços
val auth = FirebaseConfig.getAuth()
val firestore = FirebaseConfig.getFirestore()
val storage = FirebaseConfig.getStorage()
val analytics = FirebaseConfig.getAnalytics()
val messaging = FirebaseConfig.getMessaging()
```

### 2. FirebaseAuthManager

Gerenciador de autenticação que substitui o LocalAuthManager:

**Funcionalidades:**
- Cadastro de usuários (clientes e prestadores)
- Login com email e senha
- Recuperação de senha
- Gerenciamento de sessão
- Verificação de usuário

**Uso:**
```kotlin
val authManager = FirebaseAuthManager(context)

// Cadastro
val userData = FirebaseAuthManager.UserData(
    uid = "",
    email = "user@example.com",
    name = "Nome do Usuário",
    phone = "11999999999",
    userType = FirebaseAuthManager.USER_TYPE_CLIENT
)
val result = authManager.signUp(email, password, userData)

// Login
val result = authManager.signIn(email, password)

// Verificar se está logado
val isLoggedIn = authManager.isUserLoggedIn()
```

### 3. FirebaseOrderManager

Gerenciador de pedidos usando Firestore:

**Funcionalidades:**
- Criar pedidos
- Listar pedidos por cliente/prestador
- Atualizar status de pedidos
- Adicionar avaliações
- Gerenciar imagens e documentos

**Uso:**
```kotlin
val orderManager = FirebaseOrderManager()

// Criar pedido
val order = FirebaseOrderManager.Order(
    clientId = "user_id",
    clientName = "Nome do Cliente",
    serviceType = "Limpeza",
    description = "Limpeza residencial",
    address = "Rua Example, 123",
    budget = 150.0
)
val result = orderManager.createOrder(order)

// Listar pedidos do cliente
val orders = orderManager.getOrdersByClient("user_id")

// Atualizar status
orderManager.updateOrderStatus("order_id", "accepted", "provider_id", "Nome do Prestador")
```

### 4. FirebaseStorageManager

Gerenciador de armazenamento para upload de arquivos:

**Funcionalidades:**
- Upload de imagens
- Upload de documentos
- Upload de múltiplos arquivos
- Exclusão de arquivos
- Organização por pastas

**Uso:**
```kotlin
val storageManager = FirebaseStorageManager()

// Upload de imagem
val result = storageManager.uploadImage(context, imageUri, "profiles")

// Upload de múltiplas imagens
val imageUris = listOf(uri1, uri2, uri3)
val result = storageManager.uploadMultipleImages(context, imageUris, "orders")

// Upload de documentos
val result = storageManager.uploadDocument(context, documentUri, "documents")
```

### 5. FirebaseChatManager

Gerenciador de chat em tempo real:

**Funcionalidades:**
- Enviar mensagens
- Receber mensagens em tempo real
- Gerenciar salas de chat
- Marcar mensagens como lidas
- Suporte a imagens e documentos

**Uso:**
```kotlin
val chatManager = FirebaseChatManager()

// Enviar mensagem
val message = FirebaseChatManager.ChatMessage(
    orderId = "order_id",
    senderId = "user_id",
    senderName = "Nome do Usuário",
    senderType = "client",
    message = "Olá, como está o serviço?"
)
val result = chatManager.sendMessage(message)

// Receber mensagens em tempo real
chatManager.getMessagesFlow("order_id").collect { messages ->
    // Atualizar UI com novas mensagens
}
```

### 6. FirebaseNotificationManager

Gerenciador de notificações push:

**Funcionalidades:**
- Salvar tokens FCM
- Criar notificações
- Marcar como lidas
- Contar notificações não lidas
- Notificações específicas por tipo

**Uso:**
```kotlin
val notificationManager = FirebaseNotificationManager(context)

// Salvar token do usuário
val result = notificationManager.saveUserToken("user_id")

// Criar notificação de atualização de pedido
val result = notificationManager.createOrderUpdateNotification(
    userId = "user_id",
    orderId = "order_id",
    title = "Pedido Atualizado",
    message = "Seu pedido foi aceito por um prestador"
)

// Listar notificações
val notifications = notificationManager.getNotificationsForUser("user_id")
```

### 7. FirebaseMessagingService

Serviço para receber notificações push:

**Funcionalidades:**
- Receber notificações em background
- Processar dados da notificação
- Exibir notificações locais
- Navegação para telas específicas

## Estrutura do Firestore

### Coleções Principais

1. **users**
   - Dados dos usuários (clientes e prestadores)
   - Campos: uid, email, name, phone, userType, isVerified, profileImageUrl

2. **orders**
   - Pedidos de serviços
   - Campos: clientId, serviceType, description, status, budget, providerId, etc.

3. **messages**
   - Mensagens do chat
   - Campos: orderId, senderId, message, timestamp, isRead

4. **chatRooms**
   - Salas de chat
   - Campos: orderId, clientId, providerId, lastMessage, unreadCount

5. **notifications**
   - Notificações do usuário
   - Campos: userId, title, message, type, isRead, timestamp

6. **userTokens**
   - Tokens FCM dos usuários
   - Campos: userId, fcmToken, deviceType

## Regras de Segurança do Firestore

Para implementar regras de segurança adequadas, configure no console do Firebase:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Usuários podem ler/escrever apenas seus próprios dados
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Pedidos: clientes veem seus pedidos, prestadores veem pedidos disponíveis
    match /orders/{orderId} {
      allow read: if request.auth != null && 
        (resource.data.clientId == request.auth.uid || 
         resource.data.providerId == request.auth.uid ||
         resource.data.status == 'pending');
      allow create: if request.auth != null;
      allow update: if request.auth != null && 
        (resource.data.clientId == request.auth.uid || 
         resource.data.providerId == request.auth.uid);
    }
    
    // Mensagens: apenas participantes do pedido
    match /messages/{messageId} {
      allow read, write: if request.auth != null && 
        exists(/databases/$(database)/documents/orders/$(resource.data.orderId)) &&
        (get(/databases/$(database)/documents/orders/$(resource.data.orderId)).data.clientId == request.auth.uid ||
         get(/databases/$(database)/documents/orders/$(resource.data.orderId)).data.providerId == request.auth.uid);
    }
  }
}
```

## Próximos Passos

1. **Implementar login social** (Google, Facebook)
2. **Adicionar regras de segurança** no Firestore
3. **Configurar Cloud Functions** para automações
4. **Implementar pagamentos** com Firebase Extensions
5. **Adicionar analytics** personalizados
6. **Configurar Crashlytics** para monitoramento de erros

## Testando a Implementação

1. **Compile e execute** o aplicativo
2. **Teste o cadastro** de um novo usuário
3. **Teste o login** com as credenciais criadas
4. **Crie um pedido** e verifique no console do Firebase
5. **Teste o chat** entre cliente e prestador
6. **Verifique as notificações** push

## Suporte

Para dúvidas ou problemas com a implementação do Firebase, consulte:
- [Documentação oficial do Firebase](https://firebase.google.com/docs)
- [Firebase Console](https://console.firebase.google.com)
- [Firebase Android Codelab](https://firebase.google.com/codelabs/firebase-android) 