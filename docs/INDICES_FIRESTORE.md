# Índices Compostos Firestore — AquiResolve

> ⚠️ **Ação necessária:** Estes índices devem ser criados no Console Firebase.
> Sem eles, as queries retornam erro `FAILED_PRECONDITION` ou resultados incompletos.

## Como Criar

1. Acesse [Firebase Console > Firestore > Índices](https://console.firebase.google.com/project/aplicativoservico-143c2/firestore/indexes)
2. Clique em **"Adicionar índice"**
3. Preencha conforme a tabela abaixo
4. Ou faça deploy usando `firebase deploy --only firestore:indexes`

---

## Índices

### Coleção: `orders`

| # | Campos | Uso |
|---|--------|-----|
| 1 | `clientId` ASC, `createdAt` DESC | `ClientOrdersActivity`, `FirebaseOrderManager.getUserOrders()` |
| 2 | `clientId` ASC, `status` ASC, `createdAt` DESC | `FirebaseOrderManager.getOrdersByStatus()` |
| 3 | `assignedProvider` ASC, `createdAt` DESC | Listagem de pedidos atribuídos |
| 4 | `assignedProvider` ASC, `status` ASC | `countCompletedOrdersByProvider()` |
| 5 | `status` ASC, `createdAt` DESC | `ProviderOrdersFragment` — listagem por status |
| 6 | `providerId` ASC, `createdAt` DESC | Lista de pedidos do prestador |

### Coleção: `notifications`

| # | Campos | Uso |
|---|--------|-----|
| 7 | `userId` ASC, `timestamp` DESC | `FirebaseNotificationManager.getNotifications()` |
| 8 | `userId` ASC, `isRead` ASC | `getUnreadNotificationCount()`, `markAllAsRead()` |

### Coleção: `providers`

| # | Campos | Uso |
|---|--------|-----|
| 9 | `verificationStatus` ASC, `isActive` ASC, `rating` DESC | `FirebaseProviderManager.getAllVerifiedProviders()` |
| 10 | `isActive` ASC, `services` ARRAY CONTAINS | `findProvidersByRegionAndService()` |

### Coleção: `service_categories`

| # | Campos | Uso |
|---|--------|-----|
| 11 | `isActive` ASC, `order` ASC | `FirebaseServiceManager.getActiveCategories()` |

### Coleção: `service_types`

| # | Campos | Uso |
|---|--------|-----|
| 12 | `isActive` ASC, `order` ASC | `FirebaseServiceManager.getAllServiceTypes()` |
| 13 | `categoryId` ASC, `isActive` ASC, `order` ASC | `getServiceTypesByCategory()` |

### Coleção: `service_providers`

| # | Campos | Uso |
|---|--------|-----|
| 14 | `isVerified` ASC, `isAvailable` ASC, `rating` DESC | `FirebaseServiceManager.getVerifiedProviders()` |
| 15 | `isVerified` ASC, `isAvailable` ASC, `categories` ARRAY CONTAINS, `rating` DESC | `getProvidersByCategory()` |

### Coleção: `provider_documents`

| # | Campos | Uso |
|---|--------|-----|
| 16 | `providerId` ASC, `verificationId` ASC | Verificação de documentos |
| 17 | `userId` ASC, `uploadedAt` DESC | `FirebaseProviderDocumentManager.getUserDocuments()` |

### Coleção: `user_favorites`

| # | Campos | Uso |
|---|--------|-----|
| 18 | `userId` ASC, `createdAt` DESC | `FirebaseServiceManager.getUserFavorites()` |
| 19 | `userId` ASC, `itemId` ASC | `isFavorite()`, `removeFavorite()` |

### Coleção: `provider_reviews`

| # | Campos | Uso |
|---|--------|-----|
| 20 | `providerId` ASC, `createdAt` DESC | `FirebaseServiceManager.getProviderReviews()` |

---

## Deploy via CLI

```bash
firebase deploy --only firestore:indexes
```

---

## Como Testar

Após criar os índices:
1. Aguardar status **"Ativado"** (ícone verde) no console
2. Rodar o app e verificar no Logcat se as queries retornam dados
3. Erro comum: `FAILED_PRECONDITION: The query requires an index`

---

**Última atualização:** 16/05/2026 — Após merge dos commits de localização, estabilidade e limpeza
