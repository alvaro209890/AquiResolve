# 🏗️ Arquitetura de Pagamento para Produção

## ⚠️ Problema Atual

Atualmente, a chave secreta da Pagar.me está **hardcoded** no aplicativo Android. Isso é aceitável para **testes**, mas **extremamente perigoso em produção**.

### Por que é um problema?

1. **APK pode ser descompilado** - Qualquer pessoa pode extrair suas chaves
2. **Transações fraudulentas** - Com a chave secreta, podem fazer cobranças
3. **Comprometimento da conta** - Acesso total à API Pagar.me
4. **Responsabilidade financeira** - Você será responsável por cobranças fraudulentas

---

## ✅ Solução Recomendada: Backend Intermediário

### Arquitetura Segura

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   App       │         │   Backend   │         │  Pagar.me   │
│  Android    │────────>│   (Seu)     │────────>│     API     │
└─────────────┘         └─────────────┘         └─────────────┘
     (1)                     (2)                      (3)
  Envia dados           Processa com            Processa
  do cartão            chave secreta            pagamento
```

### Fluxo Seguro

1. **App Android** 
   - Coleta dados do cartão
   - Envia para SEU backend
   - Não possui chave secreta

2. **Seu Backend**
   - Recebe dados do app
   - Valida autenticação do usuário
   - Usa chave secreta para chamar Pagar.me
   - Retorna resultado para o app

3. **Pagar.me API**
   - Processa o pagamento
   - Retorna resultado para seu backend

---

## 🔧 Opções de Backend

### Opção 1: Firebase Cloud Functions (Recomendado)

Já está usando Firebase, então é a opção mais simples!

**Vantagens:**
- ✅ Integração nativa com seu Firebase
- ✅ Escalabilidade automática
- ✅ Sem servidor para gerenciar
- ✅ Plano gratuito generoso

**Implementação:**

```javascript
// functions/index.js
const functions = require('firebase-functions');
const axios = require('axios');

exports.processPayment = functions.https.onCall(async (data, context) => {
  // Verificar autenticação
  if (!context.auth) {
    throw new functions.https.HttpsError('unauthenticated', 'Usuário não autenticado');
  }

  const SECRET_KEY = functions.config().pagarme.secret_key;
  
  try {
    const response = await axios.post(
      'https://api.pagar.me/core/v5/orders',
      data.paymentRequest,
      {
        headers: {
          'Authorization': `Basic ${Buffer.from(SECRET_KEY + ':').toString('base64')}`,
          'Content-Type': 'application/json'
        }
      }
    );
    
    return {
      success: true,
      data: response.data
    };
  } catch (error) {
    console.error('Erro ao processar pagamento:', error);
    throw new functions.https.HttpsError('internal', 'Erro ao processar pagamento');
  }
});
```

**No App Android:**

```kotlin
// Chamar a Cloud Function
val functions = Firebase.functions
val processPayment = functions.getHttpsCallable("processPayment")

val data = hashMapOf(
    "paymentRequest" to paymentRequest
)

processPayment.call(data)
    .addOnSuccessListener { result ->
        // Pagamento processado com sucesso
    }
    .addOnFailureListener { error ->
        // Erro ao processar
    }
```

---

### Opção 2: Backend Node.js/Express

**Vantagens:**
- ✅ Controle total
- ✅ Pode hospedar em qualquer lugar
- ✅ Fácil de desenvolver

**Implementação:**

```javascript
// server.js
const express = require('express');
const axios = require('axios');
const app = express();

app.use(express.json());

const SECRET_KEY = process.env.PAGARME_SECRET_KEY;

app.post('/api/payment/process', async (req, res) => {
  try {
    // Verificar token do usuário
    const userToken = req.headers.authorization;
    // ... validar token com Firebase Admin SDK
    
    const response = await axios.post(
      'https://api.pagar.me/core/v5/orders',
      req.body,
      {
        headers: {
          'Authorization': `Basic ${Buffer.from(SECRET_KEY + ':').toString('base64')}`,
          'Content-Type': 'application/json'
        }
      }
    );
    
    res.json(response.data);
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

app.listen(3000);
```

**Hospedagem:**
- Heroku (gratuito para começar)
- Railway
- Render
- DigitalOcean
- AWS Lambda

---

### Opção 3: Backend PHP

**Vantagens:**
- ✅ Hospedagem barata
- ✅ Compatível com a maioria dos servidores

**Implementação:**

```php
<?php
// process_payment.php
header('Content-Type: application/json');

$SECRET_KEY = getenv('PAGARME_SECRET_KEY');

// Verificar autenticação do usuário
// ... validar token Firebase

$data = json_decode(file_get_contents('php://input'), true);

$ch = curl_init('https://api.pagar.me/core/v5/orders');
curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
curl_setopt($ch, CURLOPT_POST, true);
curl_setopt($ch, CURLOPT_POSTFIELDS, json_encode($data));
curl_setopt($ch, CURLOPT_HTTPHEADER, [
    'Authorization: Basic ' . base64_encode($SECRET_KEY . ':'),
    'Content-Type: application/json'
]);

$response = curl_exec($ch);
curl_close($ch);

echo $response;
?>
```

---

## 🔐 Configuração de Segurança

### 1. Variáveis de Ambiente

**Nunca coloque chaves no código!**

```bash
# .env (não commitar no Git!)
PAGARME_SECRET_KEY=sk_ca99e01207604a10858f6d6e81393b24
PAGARME_PUBLIC_KEY=pk_K4dq6VRTXbsyDq73
PAGARME_ACCOUNT_ID=acc_2oDK6XASNRIyMzmw
```

### 2. Autenticação

Sempre verificar se o usuário está autenticado antes de processar pagamento:

```kotlin
// No backend
if (!isUserAuthenticated(request)) {
    return error("Unauthorized");
}

if (!isOrderOwnedByUser(orderId, userId)) {
    return error("Forbidden");
}
```

### 3. Rate Limiting

Limitar número de tentativas de pagamento:

```javascript
// Exemplo com express-rate-limit
const rateLimit = require('express-rate-limit');

const paymentLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutos
  max: 5 // máximo 5 tentativas
});

app.use('/api/payment', paymentLimiter);
```

---

## 📱 Modificações no App Android

### 1. Criar PagarMeBackendService

```kotlin
interface PagarMeBackendService {
    @POST("api/payment/process")
    suspend fun processPayment(
        @Header("Authorization") token: String,
        @Body paymentRequest: PaymentRequest
    ): Response<PaymentResponse>
}
```

### 2. Atualizar PagarMeManager

```kotlin
class PagarMeManager(private val context: Context) {
    
    companion object {
        private const val BACKEND_URL = "https://seu-backend.com/"
        // Remover SECRET_KEY daqui!
    }
    
    suspend fun processPayment(...): PaymentResult {
        // Obter token do Firebase Auth
        val user = FirebaseAuth.getInstance().currentUser
        val token = user?.getIdToken(false)?.await()?.token
        
        // Chamar SEU backend
        val response = backendService.processPayment(
            token = "Bearer $token",
            paymentRequest = paymentRequest
        )
        
        // Processar resposta
        // ...
    }
}
```

---

## 🚀 Implementação Rápida (Passo a Passo)

### Usando Firebase Cloud Functions

1. **Instalar Firebase CLI**
```bash
npm install -g firebase-tools
firebase login
```

2. **Inicializar Functions**
```bash
cd /home/acer/Documentos/app
firebase init functions
```

3. **Instalar dependências**
```bash
cd functions
npm install axios
```

4. **Criar função**
```bash
# Copiar código da função acima para functions/index.js
```

5. **Configurar chave secreta**
```bash
firebase functions:config:set pagarme.secret_key="sk_ca99e01207604a10858f6d6e81393b24"
```

6. **Deploy**
```bash
firebase deploy --only functions
```

7. **Atualizar app Android**
```kotlin
// Adicionar dependência
implementation 'com.google.firebase:firebase-functions'

// Usar a função
val functions = Firebase.functions
val processPayment = functions.getHttpsCallable("processPayment")
```

---

## 📊 Comparação de Custos

| Solução | Custo Inicial | Custo por 1000 req | Complexidade |
|---------|---------------|-------------------|--------------|
| Firebase Functions | Gratuito | ~$0.40 | ⭐ Baixa |
| Heroku | Gratuito | Gratuito (hobby) | ⭐⭐ Média |
| Node.js VPS | $5/mês | Ilimitado | ⭐⭐⭐ Alta |
| AWS Lambda | Gratuito | ~$0.20 | ⭐⭐⭐ Alta |

---

## ✅ Checklist de Migração

- [ ] Escolher solução de backend
- [ ] Configurar servidor/function
- [ ] Mover chave secreta para variável de ambiente
- [ ] Implementar endpoint de pagamento
- [ ] Adicionar autenticação
- [ ] Adicionar rate limiting
- [ ] Atualizar app Android
- [ ] Testar em ambiente de desenvolvimento
- [ ] Remover chave secreta do código do app
- [ ] Deploy em produção
- [ ] Monitorar logs e erros

---

## 🎯 Recomendação Final

Para seu projeto, recomendo **Firebase Cloud Functions** porque:

1. ✅ Você já usa Firebase
2. ✅ Configuração rápida (< 1 hora)
3. ✅ Escalabilidade automática
4. ✅ Plano gratuito suficiente para começar
5. ✅ Integração nativa com Firebase Auth

---

## 📞 Precisa de Ajuda?

Se precisar de ajuda para implementar o backend, posso:
- Criar o código completo da Cloud Function
- Ajudar na configuração do Firebase
- Modificar o app Android para usar o backend

Basta pedir! 🚀









































