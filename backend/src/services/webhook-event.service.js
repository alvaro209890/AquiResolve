const { initializeFirebase } = require('../config/firebase');
const logger = require('../utils/logger');

/**
 * Idempotência do webhook Pagar.me.
 *
 * Cada evento entregue traz um `id` próprio (diferente do id do pedido no
 * gateway). Antes de processar, o handler "reivindica" o evento criando o doc
 * `payment_webhook_events/{eventId}` com `create()` — que é atômico e falha
 * com ALREADY_EXISTS se outra entrega (retry/duplicata) já o criou. Duplicata
 * responde 200 sem reprocessar; em erro de processamento o claim é liberado
 * (doc apagado) para o retry da Pagar.me funcionar.
 *
 * Fail-open por design: se o Firestore estiver indisponível, seguimos
 * processando — o handler reconsulta o status real no gateway, então
 * processar duas vezes é inofensivo; nunca processar é o cenário ruim.
 */

const COLLECTION = 'payment_webhook_events';
const ALREADY_EXISTS_CODE = 6; // gRPC ALREADY_EXISTS

function normalizeId(value) {
  return typeof value === 'string' ? value.trim() : '';
}

/**
 * Extrai o id do EVENTO (não do pedido) do payload do webhook.
 * Retorna '' quando o payload não tem id de evento distinto do id do pedido
 * (nesse caso não dá pra deduplicar com segurança e o handler processa direto).
 */
function extractWebhookEventId(payload, gatewayOrderId) {
  const eventId = normalizeId(payload && payload.id);
  if (!eventId || eventId === normalizeId(gatewayOrderId)) {
    return '';
  }
  return eventId;
}

function getFirestoreOrNull() {
  try {
    const admin = initializeFirebase();
    if (!admin.apps || admin.apps.length === 0) {
      return null;
    }
    return admin.firestore();
  } catch (_) {
    return null;
  }
}

/**
 * @returns {Promise<boolean>} true = evento reivindicado (processar);
 *                             false = duplicata (já processado/processando).
 */
async function claimWebhookEvent(eventId, metadata = {}) {
  const firestore = getFirestoreOrNull();
  if (!firestore) {
    logger.warn('Idempotência de webhook indisponível (Firestore não inicializado); processando mesmo assim', {
      eventId
    });
    return true;
  }

  const admin = initializeFirebase();
  try {
    await firestore.collection(COLLECTION).doc(eventId).create({
      status: 'processing',
      receivedAt: admin.firestore.FieldValue.serverTimestamp(),
      ...metadata
    });
    return true;
  } catch (error) {
    if (error && (error.code === ALREADY_EXISTS_CODE || /already exists/i.test(error.message || ''))) {
      return false;
    }
    logger.warn('Falha ao registrar claim de idempotência do webhook; processando mesmo assim', {
      eventId,
      error: error.message
    });
    return true;
  }
}

async function markWebhookEventProcessed(eventId, result = {}) {
  const firestore = getFirestoreOrNull();
  if (!firestore) return;

  const admin = initializeFirebase();
  try {
    await firestore.collection(COLLECTION).doc(eventId).update({
      status: 'processed',
      processedAt: admin.firestore.FieldValue.serverTimestamp(),
      ...result
    });
  } catch (error) {
    logger.warn('Falha ao marcar evento de webhook como processado', {
      eventId,
      error: error.message
    });
  }
}

/**
 * Libera o claim quando o processamento falhou, para o retry da Pagar.me
 * não cair na deduplicação.
 */
async function releaseWebhookEvent(eventId) {
  const firestore = getFirestoreOrNull();
  if (!firestore) return;

  try {
    await firestore.collection(COLLECTION).doc(eventId).delete();
  } catch (error) {
    logger.warn('Falha ao liberar claim de evento de webhook', {
      eventId,
      error: error.message
    });
  }
}

module.exports = {
  extractWebhookEventId,
  claimWebhookEvent,
  markWebhookEventProcessed,
  releaseWebhookEvent
};
