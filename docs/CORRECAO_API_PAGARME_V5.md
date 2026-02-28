# 🔧 Correção da Estrutura da API Pagar.me v5

## ❌ Erro Encontrado

```
❌ Erro na requisição PIX: 422
"The payments field is required"
```

### **Causa:**
A estrutura dos models estava **incorreta** para a API v5 da Pagar.me.

---

## 📚 Pesquisa da API Oficial

### **API Pagar.me v5 (Core)**
- **Base URL**: `https://api.pagar.me/core/v5/`
- **Endpoint**: `POST /orders`
- **Autenticação**: Basic Auth (Base64 encoded `secret_key:`)

### **Estrutura CORRETA da API v5:**

```json
{
  "items": [
    {
      "amount": 14500,
      "description": "Descrição do item",
      "quantity": 1,
      "code": "ITEM_CODE"
    }
  ],
  "customer": {
    "name": "Nome do Cliente",
    "email": "email@exemplo.com",
    "document": "12345678900",
    "document_type": "cpf",
    "type": "individual",
    "phones": {
      "mobile_phone": {
        "country_code": "55",
        "area_code": "11",
        "number": "987654321"
      }
    }
  },
  "payments": [                    // ✅ CAMPO OBRIGATÓRIO!
    {
      "payment_method": "pix",     // ou "credit_card"
      "pix": {                     // Dados específicos do PIX
        "expires_in": 3600
      }
    }
  ],
  "metadata": {
    "order_id": "123",
    "platform": "android"
  },
  "closed": true
}
```

---

## ✅ Correções Implementadas

### **1. PixPaymentRequest.kt - Corrigido**

#### **ANTES (Incorreto):**
```kotlin
data class PixPaymentRequest(
    val amount: Long,
    val payment_method: String = "pix",
    val pix: PixData,              // ❌ Errado - não é assim na v5
    val customer: CustomerInfo,
    val items: List<PaymentItem>
)
```

#### **AGORA (Correto):**
```kotlin
data class PixPaymentRequest(
    val items: List<PaymentItem>,
    val customer: CustomerInfo,
    val payments: List<PixPaymentMethod>,  // ✅ Array de payments!
    val metadata: Map<String, String>?,
    val closed: Boolean = true
)

data class PixPaymentMethod(
    val payment_method: String = "pix",
    val pix: PixData                       // ✅ Dentro do payment!
)
```

---

### **2. PaymentRequest.kt (Cartão) - Corrigido**

#### **ANTES (Incorreto):**
```kotlin
data class PaymentRequest(
    val amount: Long,
    val payment_method: String = "credit_card",
    val card: CardData,            // ❌ Errado
    val billing: BillingData,      // ❌ Errado
    val customer: CustomerInfo,
    val items: List<PaymentItem>
)
```

#### **AGORA (Correto):**
```kotlin
data class PaymentRequest(
    val items: List<PaymentItem>,
    val customer: CustomerInfo,
    val payments: List<CreditCardPaymentMethod>,  // ✅ Array!
    val metadata: Map<String, String>?,
    val closed: Boolean = true
)

data class CreditCardPaymentMethod(
    val payment_method: String = "credit_card",
    val credit_card: CreditCardData            // ✅ Dentro do payment!
)

data class CreditCardData(
    val card: CardData,
    val billing_address: BillingAddress        // ✅ Estrutura correta!
)
```

---

### **3. PagarMeManager.kt - Atualizado**

#### **Método PIX - Corrigido:**
```kotlin
suspend fun processPixPayment(...): PixPaymentResult {
    // Criar item
    val item = PaymentItem(...)
    
    // Criar dados PIX
    val pixData = PixData(expiresIn = 3600, ...)
    
    // ✅ Criar método de pagamento (estrutura v5)
    val pixPaymentMethod = PixPaymentMethod(
        paymentMethod = "pix",
        pix = pixData
    )
    
    // ✅ Criar requisição com payments[]
    val pixRequest = PixPaymentRequest(
        items = listOf(item),
        customer = customerInfo,
        payments = listOf(pixPaymentMethod),  // ✅ Campo obrigatório!
        metadata = metadata,
        closed = true
    )
    
    // Enviar para API
    val response = apiService.createPixOrder(authToken, pixRequest)
}
```

#### **Método Cartão - Corrigido:**
```kotlin
suspend fun processPayment(...): PaymentResult {
    // Criar dados de cobrança
    val billing = BillingAddress(...)
    
    // ✅ Criar dados do cartão (estrutura v5)
    val creditCardData = CreditCardData(
        card = cardData,
        billingAddress = billing
    )
    
    // ✅ Criar método de pagamento (estrutura v5)
    val creditCardPaymentMethod = CreditCardPaymentMethod(
        paymentMethod = "credit_card",
        creditCard = creditCardData
    )
    
    // ✅ Criar requisição com payments[]
    val paymentRequest = PaymentRequest(
        items = listOf(item),
        customer = customerInfo,
        payments = listOf(creditCardPaymentMethod),  // ✅ Campo obrigatório!
        metadata = metadata,
        closed = true
    )
    
    // Enviar para API
    val response = apiService.createOrder(authToken, paymentRequest)
}
```

---

## 📊 Estrutura Completa da API v5

### **Hierarquia:**
```
Order (Pedido)
├── items[] (Lista de itens)
│   ├── amount
│   ├── description
│   ├── quantity
│   └── code
├── customer (Dados do cliente)
│   ├── name
│   ├── email
│   ├── document
│   ├── document_type
│   ├── type
│   └── phones
│       └── mobile_phone
│           ├── country_code
│           ├── area_code
│           └── number
├── payments[] (✅ OBRIGATÓRIO - Lista de pagamentos)
│   └── [0]
│       ├── payment_method: "pix" ou "credit_card"
│       ├── pix: {...}              (se PIX)
│       │   └── expires_in
│       └── credit_card: {...}      (se Cartão)
│           ├── card
│           │   ├── card_number
│           │   ├── card_holder_name
│           │   ├── card_expiration_date
│           │   └── card_cvv
│           └── billing_address
│               ├── line_1
│               ├── zip_code
│               ├── city
│               ├── state
│               └── country
├── metadata (Informações adicionais)
└── closed: true
```

---

## 🔍 Diferenças v5 vs Versões Anteriores

| Campo | API Antiga | API v5 |
|-------|------------|--------|
| Estrutura | Flat (campos diretos) | Nested (payments[]) |
| Amount | No root | Só nos items |
| Payment Method | Campo direto | Dentro de payments[] |
| PIX | Campo direto | Dentro de payments[0].pix |
| Card | Campo direto | Dentro de payments[0].credit_card |
| Billing | Campo direto | Dentro de credit_card.billing_address |

---

## 📝 JSON Enviado (Exemplo Real PIX)

```json
{
  "items": [
    {
      "amount": 14500,
      "description": "Elétrica - Instalação de lâmpadas",
      "quantity": 1,
      "code": "pending"
    }
  ],
  "customer": {
    "name": "Alvaro Emanuel",
    "email": "alvaroe@gmail.com",
    "document": "04438470102",
    "document_type": "cpf",
    "type": "individual",
    "phones": {
      "mobile_phone": {
        "country_code": "55",
        "area_code": "11",
        "number": "999999999"
      }
    }
  },
  "payments": [
    {
      "payment_method": "pix",
      "pix": {
        "expires_in": 3600,
        "additional_information": [
          {
            "name": "Pedido",
            "value": "pending"
          }
        ]
      }
    }
  ],
  "metadata": {
    "order_id": "pending",
    "platform": "android"
  },
  "closed": true
}
```

---

## 🔧 Logs de Debug Adicionados

```kotlin
// Agora o sistema loga detalhadamente:

Log.d(TAG, "=== REQUISIÇÃO PIX ===")
Log.d(TAG, "Customer: ${customerInfo.name} | CPF: ${customerInfo.document}")
Log.d(TAG, "Items: ${item.description} | Valor: ${item.amount} centavos")
Log.d(TAG, "Payment Method: pix | Expires: 3600s")
Log.d(TAG, "Enviando requisição PIX para Pagar.me...")

// Resposta:
Log.d(TAG, "Resposta PIX recebida - Status: ${pixResponse.status}")
Log.d(TAG, "✅ PIX gerado com sucesso!")
```

---

## ✅ Validação de CPF Melhorada

### **Logs adicionados em todas as etapas:**

```kotlin
// 1. Ao receber dados (PixPaymentActivity)
Log.d("PixPayment", "Dados recebidos:")
Log.d("PixPayment", "- CPF: $clientCpf (${clientCpf.length} dígitos)")

// 2. Ao limpar CPF
clientCpf = clientCpf.replace(Regex("[^\\d]"), "")

// 3. Ao validar
Log.d("PixPayment", "Validando CPF: '$cleanCpf' (tamanho: ${cleanCpf.length})")

// 4. Ao preparar CustomerInfo
Log.d("PixPayment", "Preparando pagamento PIX:")
Log.d("PixPayment", "- CPF: $clientCpf")
Log.d("PixPayment", "- Telefone: DDD=$areaCode, Número=$phoneNumber")

// 5. Após criar CustomerInfo
Log.d("PixPayment", "CustomerInfo criado: ${customerInfo.document}")
```

---

## 🎯 Resultado das Correções

### **O que foi corrigido:**

1. ✅ **Estrutura da API v5** - Formato correto com `payments[]`
2. ✅ **PIX** - Campo `payments[0].pix` corretamente aninhado
3. ✅ **Cartão** - Campo `payments[0].credit_card` corretamente aninhado
4. ✅ **Billing Address** - Dentro de `credit_card.billing_address`
5. ✅ **Closed** - Campo adicionado (indica ordem fechada)
6. ✅ **Logs detalhados** - Debug em cada etapa
7. ✅ **Validação CPF** - Múltiplas camadas com logs

---

## 📱 Testando Agora

### **Quando testar PIX novamente:**

1. **Observe os logs** no Logcat (filtrar por "PixPayment" ou "PagarMeManager")
2. **Verifique CPF** nos logs:
   ```
   Dados recebidos:
   - CPF: 04438470102 (11 dígitos)    ✅ Correto!
   ```
3. **Verifique requisição**:
   ```
   === REQUISIÇÃO PIX ===
   Customer: Alvaro Emanuel | CPF: 04438470102
   Items: Elétrica - Instalação de lâmpadas | Valor: 14500 centavos
   Payment Method: pix | Expires: 3600s
   ```
4. **API deve retornar 200 OK** com o QR Code

---

## 🔷 Fluxo PIX Completo Corrigido

```
Cliente → Preenche pedido → Salva temporário
   ↓
Vai para tela de pagamento
   ↓
Escolhe PIX
   ↓
PixPaymentActivity
   ├─ Valida CPF (11 dígitos)
   ├─ Se vazio → Mostra campo de entrada
   ├─ Limpa CPF (apenas números)
   └─ Logs detalhados
   ↓
Clica "Gerar Código PIX"
   ↓
PagarMeManager.processPixPayment()
   ├─ Cria CustomerInfo com CPF limpo
   ├─ Cria PixData (expires_in: 3600)
   ├─ Cria PixPaymentMethod
   ├─ Cria PixPaymentRequest com payments[] ✅
   └─ Logs: "=== REQUISIÇÃO PIX ==="
   ↓
Envia para API Pagar.me
   ├─ POST /orders
   ├─ Basic Auth com secret_key
   └─ JSON com estrutura v5 correta
   ↓
API retorna:
   ├─ Status: "pending"
   ├─ QR Code
   ├─ QR Code URL
   └─ Expires at
   ↓
Exibe QR Code na tela
   ├─ Bitmap gerado (ZXing)
   ├─ Código copia e cola
   ├─ Timer de expiração
   └─ Botão verificar pagamento
   ↓
Cliente paga no banco
   ↓
App verifica status
   ├─ checkPixPaymentStatus()
   └─ GET /orders/{orderId}
   ↓
Se status = "paid":
   ├─ Retorna RESULT_PAYMENT_SUCCESS
   └─ CreateOrderActivity cria pedido no Firebase ✅
```

---

## 📊 Comparação das Estruturas

### **PIX - Estrutura Corrigida:**

| Antes (❌ Errado) | Agora (✅ Correto) |
|-------------------|-------------------|
| `amount` no root | `amount` só nos items |
| `payment_method` no root | `payment_method` em payments[0] |
| `pix` no root | `pix` em payments[0].pix |
| Sem `closed` | `closed: true` |
| Sem `payments[]` | `payments: [...]` ✅ |

### **Cartão - Estrutura Corrigida:**

| Antes (❌ Errado) | Agora (✅ Correto) |
|-------------------|-------------------|
| `card` no root | `card` em payments[0].credit_card.card |
| `billing` no root | `billing_address` em credit_card |
| Sem `payments[]` | `payments: [...]` ✅ |

---

## 🧪 Testando a Correção

### **O que observar nos logs:**

```
// 1. Dados recebidos
PixPayment: Dados recebidos:
PixPayment: - CPF: 04438470102 (11 dígitos)      ✅
PixPayment: - Nome: Alvaro emanel
PixPayment: - Email: alvaroe@gmail.com

// 2. Validação
PixPayment: Validando CPF: '04438470102' (tamanho: 11)  ✅

// 3. Preparação
PixPayment: Preparando pagamento PIX:
PixPayment: - CPF: 04438470102
PixPayment: - Telefone: DDD=11, Número=999999999
PixPayment: - Valor: R$ 145.0 (14500 centavos)

// 4. Requisição
PagarMeManager: === REQUISIÇÃO PIX ===
PagarMeManager: Customer: Alvaro emanel | CPF: 04438470102
PagarMeManager: Items: Elétrica - Instalação de lâmpadas | Valor: 14500 centavos
PagarMeManager: Payment Method: pix | Expires: 3600s
PagarMeManager: Enviando requisição PIX para Pagar.me...

// 5. Resposta (deve ser sucesso agora!)
PagarMeManager: Resposta PIX recebida - Status: pending    ✅
PagarMeManager: ✅ PIX gerado com sucesso!
```

### **Se der erro 422 novamente:**
- Verificar se o CPF tem exatamente 11 dígitos
- Verificar se o telefone está no formato correto
- Verificar os logs detalhados
- Verificar se a chave secreta está correta

---

## 🎯 Checklist de Validações

Antes de enviar para API:

- ✅ CPF: 11 dígitos numéricos
- ✅ Telefone: DDD (2 dígitos) + Número (9 dígitos)
- ✅ Email: formato válido
- ✅ Valor: em centavos (Long)
- ✅ Items: array com pelo menos 1 item
- ✅ Payments: array com pelo menos 1 payment
- ✅ Customer: todos os campos obrigatórios
- ✅ Closed: true

---

## 📁 Arquivos Corrigidos

```
✅ models/payment/PixPaymentRequest.kt (estrutura v5)
✅ models/payment/PaymentRequest.kt (estrutura v5)
✅ payment/PagarMeManager.kt (métodos atualizados)
✅ CreateOrderActivity.kt (fluxo corrigido)
```

---

## 🚀 Resultado Final

O sistema agora:

✅ **Usa estrutura correta da API v5**
✅ **Campo `payments[]` obrigatório incluído**
✅ **PIX com estrutura aninhada correta**
✅ **Cartão com estrutura aninhada correta**
✅ **Logs detalhados em cada etapa**
✅ **Validações de CPF robustas**
✅ **Pedido criado APENAS após pagamento**

---

**Teste novamente e verifique os logs!** 🎉

O erro 422 "payments field is required" deve estar resolvido agora!





































