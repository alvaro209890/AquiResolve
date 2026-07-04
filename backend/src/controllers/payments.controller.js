const HttpError = require('../utils/http-error');
const { createOrder, getOrderStatus } = require('../services/pagarme.service');
const { authorizePaymentPayload } = require('../services/payment-authorization.service');
const {
  ensurePaymentSessionOwnership,
  getPaymentSession,
  savePaymentSession,
  updatePaymentSessionStatus
} = require('../services/payment-session.service');
const {
  syncPaymentStatusToFirestore
} = require('../services/payment-status-sync.service');
const {
  settleCompletedOrder: settleCompletedOrderService
} = require('../services/order-settlement.service');
const {
  normalizeOrderResponse,
  mapPagarmeError
} = require('../services/payment-mapper.service');
const {
  extractWebhookEventId,
  claimWebhookEvent,
  markWebhookEventProcessed,
  releaseWebhookEvent
} = require('../services/webhook-event.service');
const { verifyWebhookRequest } = require('../utils/webhook-auth');
const { loadEnv } = require('../config/env');
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
    validatePaymentPayload(req.body);
    const authorizedPayload = await authorizePaymentPayload({
      payload: req.body,
      uid: req.user && req.user.uid
    });

    logger.info('Recebida solicitacao de pagamento com cartao', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderCode: authorizedPayload?.items?.[0]?.code || null,
      itemCount: Array.isArray(authorizedPayload?.items) ? authorizedPayload.items.length : 0,
      paymentCount: Array.isArray(authorizedPayload?.payments) ? authorizedPayload.payments.length : 0
    });

    const order = await createOrder(authorizedPayload);
    const session = await savePaymentSession({
      gatewayOrderId: order?.id,
      uid: req.user && req.user.uid,
      localOrderCode:
        (authorizedPayload?.metadata && authorizedPayload.metadata.order_id) ||
        authorizedPayload?.items?.[0]?.code ||
        null,
      paymentSource: authorizedPayload?.metadata?.payment_source || null
    });
    const syncResult = session
      ? await syncPaymentStatusToFirestore({ gatewayOrder: order, session })
      : null;
    if (syncResult) {
      await updatePaymentSessionStatus({
        gatewayOrderId: order?.id,
        paymentStatus: syncResult.paymentStatus,
        gatewayStatus: order?.status
      });
    }
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
    validatePaymentPayload(req.body);
    const authorizedPayload = await authorizePaymentPayload({
      payload: req.body,
      uid: req.user && req.user.uid
    });

    logger.info('Recebida solicitacao de pagamento PIX', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderCode: authorizedPayload?.items?.[0]?.code || null,
      itemCount: Array.isArray(authorizedPayload?.items) ? authorizedPayload.items.length : 0,
      paymentCount: Array.isArray(authorizedPayload?.payments) ? authorizedPayload.payments.length : 0
    });

    const order = await createOrder(authorizedPayload);
    const session = await savePaymentSession({
      gatewayOrderId: order?.id,
      uid: req.user && req.user.uid,
      localOrderCode:
        (authorizedPayload?.metadata && authorizedPayload.metadata.order_id) ||
        authorizedPayload?.items?.[0]?.code ||
        null,
      paymentSource: authorizedPayload?.metadata?.payment_source || null
    });
    const syncResult = session
      ? await syncPaymentStatusToFirestore({ gatewayOrder: order, session })
      : null;
    if (syncResult) {
      await updatePaymentSessionStatus({
        gatewayOrderId: order?.id,
        paymentStatus: syncResult.paymentStatus,
        gatewayStatus: order?.status
      });
    }
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

    const session = await ensurePaymentSessionOwnership({
      gatewayOrderId: orderId.trim(),
      uid: req.user && req.user.uid
    });
    const order = await getOrderStatus(orderId.trim());
    const syncResult = await syncPaymentStatusToFirestore({ gatewayOrder: order, session });
    await updatePaymentSessionStatus({
      gatewayOrderId: order?.id || orderId.trim(),
      paymentStatus: syncResult.paymentStatus,
      gatewayStatus: order?.status
    });
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

async function settleCompletedOrder(req, res, next) {
  try {
    const { orderId } = req.params;

    if (!orderId || typeof orderId !== 'string' || !orderId.trim()) {
      throw new HttpError(400, 'orderId inválido', {
        code: 'INVALID_ORDER_ID'
      });
    }

    logger.info('Solicitada liquidacao financeira de pedido', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: orderId.trim()
    });

    const settlement = await settleCompletedOrderService({
      orderId: orderId.trim(),
      actorUid: req.user && req.user.uid,
      actorType: 'mobile'
    });

    res.status(200).json({
      success: true,
      settlement
    });
  } catch (error) {
    logger.warn('Falha ao liquidar pedido concluido', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      orderId: req.params?.orderId || null,
      error: error.message
    });
    next(error);
  }
}

function verifyWebhookSecret(req) {
  const { pagarmeWebhookSecret } = loadEnv();
  const result = verifyWebhookRequest({
    headers: req.headers,
    query: req.query,
    rawBody: req.rawBody,
    secret: pagarmeWebhookSecret
  });

  if (result.method === 'none-configured') {
    logger.warn('PAGARME_WEBHOOK_SECRET ausente; webhook aceito sem validação de autenticidade', {
      requestId: req.requestId
    });
    return;
  }

  if (!result.ok) {
    throw new HttpError(401, 'Webhook de pagamento nao autorizado', {
      code: 'UNAUTHORIZED_WEBHOOK'
    });
  }

  logger.info('Webhook Pagar.me autenticado', {
    requestId: req.requestId,
    method: result.method
  });
}

function extractGatewayOrderIdFromWebhook(payload) {
  return normalizeWebhookString(payload?.data?.id) ||
    normalizeWebhookString(payload?.data?.order?.id) ||
    normalizeWebhookString(payload?.data?.object?.id) ||
    normalizeWebhookString(payload?.data?.object?.order?.id) ||
    normalizeWebhookString(payload?.order?.id) ||
    normalizeWebhookString(payload?.object?.id) ||
    normalizeWebhookString(payload?.id);
}

function normalizeWebhookString(value) {
  return typeof value === 'string' ? value.trim() : '';
}

async function handlePagarmeWebhook(req, res, next) {
  let claimedEventId = '';
  try {
    verifyWebhookSecret(req);

    const eventPayload = req.body || {};
    const gatewayOrderId = extractGatewayOrderIdFromWebhook(eventPayload);
    if (!gatewayOrderId) {
      throw new HttpError(400, 'Webhook sem identificador de pedido do gateway', {
        code: 'WEBHOOK_MISSING_ORDER_ID'
      });
    }

    // Idempotência: cada evento é processado uma única vez (retries/duplicatas
    // da Pagar.me respondem 200 sem reprocessar).
    const eventId = extractWebhookEventId(eventPayload, gatewayOrderId);
    if (eventId) {
      const claimed = await claimWebhookEvent(eventId, {
        gatewayOrderId,
        eventType: normalizeWebhookString(eventPayload.type)
      });
      if (!claimed) {
        logger.info('Webhook Pagar.me duplicado ignorado (idempotencia)', {
          requestId: req.requestId,
          eventId,
          gatewayOrderId
        });
        res.status(200).json({ ok: true, duplicate: true, gatewayOrderId });
        return;
      }
      claimedEventId = eventId;
    }

    const session = await getPaymentSession(gatewayOrderId);
    const gatewayOrder = await getOrderStatus(gatewayOrderId);
    const syncResult = await syncPaymentStatusToFirestore({ gatewayOrder, session });
    await updatePaymentSessionStatus({
      gatewayOrderId: gatewayOrder?.id || gatewayOrderId,
      paymentStatus: syncResult.paymentStatus,
      gatewayStatus: gatewayOrder?.status
    });

    if (claimedEventId) {
      await markWebhookEventProcessed(claimedEventId, {
        paymentStatus: syncResult.paymentStatus,
        updatedOrders: syncResult.updatedOrders
      });
    }

    logger.info('Webhook Pagar.me processado com sucesso', {
      requestId: req.requestId,
      gatewayOrderId,
      eventId: claimedEventId || null,
      eventType: normalizeWebhookString(eventPayload.type),
      paymentStatus: syncResult.paymentStatus,
      updatedOrders: syncResult.updatedOrders
    });

    res.status(200).json({
      ok: true,
      gatewayOrderId,
      paymentStatus: syncResult.paymentStatus,
      updatedOrders: syncResult.updatedOrders
    });
  } catch (error) {
    // Libera o claim para o retry da Pagar.me não cair na deduplicação.
    if (claimedEventId) {
      await releaseWebhookEvent(claimedEventId);
    }
    logger.warn('Falha ao processar webhook Pagar.me', {
      requestId: req.requestId,
      error: error.message
    });
    next(mapPagarmeError(error));
  }
}

module.exports = {
  processCardPayment,
  processPixPayment,
  getPaymentStatus,
  settleCompletedOrder,
  handlePagarmeWebhook
};
