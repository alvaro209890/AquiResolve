# Firebase Setup Guide - Resolver Erro CONFIGURATION_NOT_FOUND

## Problema Atual
O erro `CONFIGURATION_NOT_FOUND` está ocorrendo porque o arquivo `google-services.json` contém valores placeholder em vez dos valores reais do seu projeto Firebase.

## Solução Completa

### 1. Acessar Firebase Console
1. Vá para [Firebase Console](https://console.firebase.google.com/)
2. Faça login com sua conta Google
3. Selecione o projeto `gasprojeto-b6797` (ou crie um novo se necessário)

### 2. Configurar Authentication
1. No menu lateral, clique em **Authentication**
2. Clique em **Get started**
3. Na aba **Sign-in method**, habilite:
   - **Email/Password** (já deve estar habilitado)
   - **Google** (opcional, para login social)
4. Clique em **Save**

### 3. Configurar Android App
1. No menu lateral, clique em **Project settings** (ícone de engrenagem)
2. Na aba **General**, role até **Your apps**
3. Clique em **Add app** → **Android**
4. Configure:
   - **Android package name**: `com.example.loginapp`
   - **App nickname**: `AppServiço` (opcional)
   - **Debug signing certificate SHA-1**: (opcional por enquanto)
5. Clique em **Register app**

### 4. Baixar google-services.json
1. Após registrar o app, o Firebase irá gerar um arquivo `google-services.json`
2. Baixe este arquivo
3. Substitua o arquivo atual em `app/google-services.json` pelo novo arquivo

### 5. Verificar Configuração
O novo arquivo `google-services.json` deve ter uma estrutura similar a esta:

```json
{
  "project_info": {
    "project_number": "700301197838",
    "project_id": "gasprojeto-b6797",
    "storage_bucket": "gasprojeto-b6797.firebasestorage.app"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:700301197838:android:1234567890abcdef",
        "android_client_info": {
          "package_name": "com.example.loginapp"
        }
      },
      "oauth_client": [
        {
          "client_id": "700301197838-abcdefghijklmnop.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyAmQn-FP2RRvq8T7r7wvtzqKPIKiH1A6SE"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "700301197838-abcdefghijklmnop.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    }
  ],
  "configuration_version": "1"
}
```

### 6. Configurar Firestore Database
1. No menu lateral, clique em **Firestore Database**
2. Clique em **Create database**
3. Escolha **Start in test mode** (para desenvolvimento)
4. Escolha a localização mais próxima (ex: `us-central1`)
5. Clique em **Done**

### 7. Configurar Storage
1. No menu lateral, clique em **Storage**
2. Clique em **Get started**
3. Escolha **Start in test mode**
4. Escolha a localização mais próxima
5. Clique em **Done**

### 8. Configurar Cloud Messaging
1. No menu lateral, clique em **Cloud Messaging**
2. O FCM já deve estar habilitado automaticamente

### 9. Testar a Aplicação
1. Limpe e recompile o projeto:
   ```bash
   ./gradlew clean
   ./gradlew build
   ```
2. Execute o app no emulador/dispositivo
3. Teste o login com as credenciais admin:
   - Email: `aquiresolveservico123@gmail.com`
   - Senha: `jocimar123`

## Estrutura do Firestore

Após configurar, o Firestore deve ter as seguintes coleções:

### Coleção: `users`
```json
{
  "userId": {
    "email": "user@example.com",
    "name": "Nome do Usuário",
    "phone": "+5511999999999",
    "userType": "client", // ou "provider"
    "createdAt": "2024-01-01T00:00:00Z",
    "profileImageUrl": "https://...",
    "isActive": true
  }
}
```

### Coleção: `orders`
```json
{
  "orderId": {
    "clientId": "userId",
    "providerId": "providerUserId",
    "title": "Título do Serviço",
    "description": "Descrição detalhada",
    "category": "Limpeza",
    "budget": 150.0,
    "status": "pending", // pending, accepted, in_progress, completed, cancelled
    "location": {
      "address": "Rua Example, 123",
      "latitude": -23.5505,
      "longitude": -46.6333
    },
    "images": ["url1", "url2"],
    "documents": ["url1"],
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z"
  }
}
```

### Coleção: `messages`
```json
{
  "messageId": {
    "orderId": "orderId",
    "senderId": "userId",
    "receiverId": "providerUserId",
    "message": "Olá, gostaria de mais informações",
    "timestamp": "2024-01-01T00:00:00Z",
    "isRead": false
  }
}
```

### Coleção: `notifications`
```json
{
  "notificationId": {
    "userId": "userId",
    "title": "Novo Pedido",
    "message": "Você recebeu um novo pedido de serviço",
    "type": "new_order",
    "orderId": "orderId",
    "isRead": false,
    "timestamp": "2024-01-01T00:00:00Z"
  }
}
```

## Regras de Segurança do Firestore

Configure as regras de segurança no Firestore:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Usuários podem ler/escrever seus próprios dados
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Pedidos - clientes podem criar, ambos podem ler/atualizar
    match /orders/{orderId} {
      allow create: if request.auth != null;
      allow read, update: if request.auth != null && 
        (resource.data.clientId == request.auth.uid || 
         resource.data.providerId == request.auth.uid);
    }
    
    // Mensagens - participantes do pedido podem ler/escrever
    match /messages/{messageId} {
      allow read, write: if request.auth != null;
    }
    
    // Notificações - usuário pode ler/escrever suas próprias
    match /notifications/{notificationId} {
      allow read, write: if request.auth != null && 
        resource.data.userId == request.auth.uid;
    }
  }
}
```

## Regras de Segurança do Storage

Configure as regras de segurança no Storage:

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Usuários autenticados podem fazer upload de imagens de perfil
    match /profile-images/{userId}/{allPaths=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
    
    // Usuários autenticados podem fazer upload de imagens de pedidos
    match /order-images/{orderId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
    
    // Usuários autenticados podem fazer upload de documentos
    match /documents/{orderId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

## Próximos Passos

1. Siga o guia acima para configurar corretamente o Firebase
2. Substitua o arquivo `google-services.json` pelo arquivo correto do Firebase Console
3. Teste a aplicação novamente
4. Se ainda houver problemas, verifique os logs do Android Studio para mais detalhes

## Contato

Se precisar de ajuda adicional, verifique:
- Logs do Android Studio (Logcat)
- Console do Firebase para erros
- Configuração da rede/internet no dispositivo/emulador 