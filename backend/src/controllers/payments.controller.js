const HttpError = require('../utils/http-error');
const { createOrder, getOrderStatus } = require('../services/pagarme.service');
const {
  normalizeOrderResponse,
  mapPagarmeError
} = require('../services/payment-mapper.service');
const logger = require('../utils/logger');

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
    logger.info('Recebida solicitacao de pagamento com cartao', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderCode: req.body?.items?.[0]?.code || null,
      itemCount: Array.isArray(req.body?.items) ? req.body.items.length : 0,
      paymentCount: Array.isArray(req.body?.payments) ? req.body.payments.length : 0
    });

    validatePaymentPayload(req.body);

    const order = await createOrder(req.body);
    logger.info('Pagamento com cartao processado', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: order?.id || null,
      status: order?.status || null
    });
    res.status(200).json(normalizeOrderResponse(order));
  } catch (error) {
    logger.warn('Falha ao processar pagamento com cartao', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      error: error.message
    });
    next(mapPagarmeError(error));
  }
}

async function processPixPayment(req, res, next) {
  try {
    logger.info('Recebida solicitacao de pagamento PIX', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderCode: req.body?.items?.[0]?.code || null,
      itemCount: Array.isArray(req.body?.items) ? req.body.items.length : 0,
      paymentCount: Array.isArray(req.body?.payments) ? req.body.payments.length : 0
    });

    validatePaymentPayload(req.body);

    const order = await createOrder(req.body);
    logger.info('Pagamento PIX processado', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: order?.id || null,
      status: order?.status || null
    });
    res.status(200).json(normalizeOrderResponse(order));
  } catch (error) {
    logger.warn('Falha ao processar pagamento PIX', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      error: error.message
    });
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

    logger.info('Consulta de status de pagamento', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: orderId.trim()
    });

    const order = await getOrderStatus(orderId.trim());
    logger.info('Status de pagamento consultado', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: order?.id || orderId.trim(),
      status: order?.status || null
    });
    res.status(200).json(normalizeOrderResponse(order));
  } catch (error) {
    logger.warn('Falha ao consultar status de pagamento', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: req.params?.orderId || null,
      error: error.message
    });
    next(mapPagarmeError(error));
  }
}

module.exports = {
  processCardPayment,
  processPixPayment,
  getPaymentStatus
};
