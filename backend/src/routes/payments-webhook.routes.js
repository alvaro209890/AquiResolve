const express = require('express');

const { handlePagarmeWebhook } = require('../controllers/payments.controller');

// Router separado de payments.routes.js de propósito: o webhook é chamado pela
// Pagar.me (sem token Firebase) e NÃO pode passar pelo paymentLimiter — uma
// rajada de eventos legítimos levaria 429 e atrasaria a confirmação de
// pagamento. A autenticidade é validada no controller (webhook-auth).
const router = express.Router();

router.post('/pagarme', handlePagarmeWebhook);

module.exports = router;
