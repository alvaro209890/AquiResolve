const HttpError = require('../utils/http-error');
const { createOrder, getOrderStatus } = require('../services/pagarme.service');
const {
  normalizeOrderResponse,
  mapPagarmeError
} = require('../services/payment-mapper.service');

function validatePaymentPayload(payload) {
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) {
    throw new HttpError(400, 'Payload de pagamento inválido', {
      code: 'INVALID_PAYLOAD'
    });
  }

  if (!Array.isArray(payload.items) || payload.items.length === 0) {
    throw new HttpError(400, 'O campo items é obrigatório', {
      code: 'INVALID_PAYLOAD'
    });
  }

  if (!payload.customer || typeof payload.customer !== 'object') {
    throw new HttpError(400, 'O campo customer é obrigatório', {
      code: 'INVALID_PAYLOAD'
    });
  }

  if (!Array.isArray(payload.payments) || payload.payments.length === 0) {
    throw new HttpError(400, 'O campo payments é obrigatório', {
      code: 'INVALID_PAYLOAD'
    });
  }
}

async function processCardPayment(req, res, next) {
  try {
    validatePaymentPayload(req.body);

    const order = await createOrder(req.body);
    res.status(200).json(normalizeOrderResponse(order));
  } catch (error) {
    next(mapPagarmeError(error));
  }
}

async function processPixPayment(req, res, next) {
  try {
    validatePaymentPayload(req.body);

    const order = await createOrder(req.body);
    res.status(200).json(normalizeOrderResponse(order));
  } catch (error) {
    next(mapPagarmeError(error));
  }
}

async function getPaymentStatus(req, res, next) {
  try {
    const { orderId } = req.params;

    if (!orderId || typeof orderId !== 'string' || !orderId.trim()) {
      throw new HttpError(400, 'orderId inválido', {
        code: 'INVALID_ORDER_ID'
      });
    }

    const order = await getOrderStatus(orderId.trim());
    res.status(200).json(normalizeOrderResponse(order));
  } catch (error) {
    next(mapPagarmeError(error));
  }
}

module.exports = {
  processCardPayment,
  processPixPayment,
  getPaymentStatus
};
