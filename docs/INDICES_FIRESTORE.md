# Índices Compostos Firestore — AquiResolve

> ⚠️ **Ação necessária:** Criar estes índices no Console Firebase antes do deploy em produção.
> Sem eles, as queries retornam erro ou resultados incompletos.

## Como Criar

1. Acesse [Firebase Console](https://console.firebase.google.com/project/aplicativoservico-143c2/firestore/indexes)
2. Clique em **"Adicionar índice"**
3. Preencha conforme a tabela abaixo

---

## Índices Pendentes

### Coleção: `orders`

| # | Campos | Ordem | Uso |
|---|--------|-------|-----|
| 1 | `clientId` ASC, `createdAt` DESC | — | `ClientOrdersActivity` — lista de pedidos do cliente |
| 2 | `clientId` ASC, `status` ASC, `createdAt` DESC | — | `FirebaseOrderManager.getOrdersByStatus()` |
| 3 | `status` ASC, `createdAt` DESC | — | Listagem de pedidos por status |
| 4 | `providerId` ASC, `createdAt` DESC | — | Lista de pedidos do prestador |

### Coleção: `notifications`

| # | Campos | Ordem | Uso |
|---|--------|-------|-----|
| 5 | `userId` ASC, `timestamp` DESC | — | `FirebaseNotificationManager` |

### Coleção: `providers`

| # | Campos | Ordem | Uso |
|---|--------|-------|-----|
| 6 | `verificationStatus` ASC, `isActive` ASC, `rating` DESC | — | `getAllVerifiedProviders()` |
| 7 | `isActive` ASC, `services` ARRAY | — | `findProvidersByRegionAndService()` |

### Coleção: `services`

| # | Campos | Ordem | Uso |
|---|--------|-------|-----|
| 8 | `isActive` ASC, `order` ASC | — | `FirebaseServiceManager` |
| 9 | `categoryId` ASC, `isActive` ASC, `order` ASC | — | Serviços por categoria |
| 10 | `isVerified` ASC, `isAvailable` ASC, `rating` DESC | — | Prestadores verificados |
| 11 | `isVerified` ASC, `isAvailable` ASC, `categories` ARRAY, `rating` DESC | — | Busca por categoria |

---

## Como Testar

Após criar os índices:
1. Aguardar status **"Ativado"** (ícone verde) no console
2. Rodar o app e verificar no Logcat se as queries retornam dados
3. Erro comum: `FAILED_PRECONDITION: The query requires an index`

---

**Criado:** 16/05/2026 — Auditoria Fase 2
