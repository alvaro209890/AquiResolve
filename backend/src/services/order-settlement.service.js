const HttpError = require('../utils/http-error');
const { initializeFirebase } = require('../config/firebase');
const logger = require('../utils/logger');

const CASHBACK_CONFIG_REF = ['app_config', 'cashback'];
const ORDERS_COLLECTION = 'orders';
const SETTLEMENTS_COLLECTION = 'order_settlements';
const CAMPAIGNS_COLLECTION = 'cashback_campaigns';

function numberFrom(value, fallback = 0) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function roundCurrency(value) {
  return Math.round(numberFrom(value) * 100) / 100;
}

function normalizeString(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function toMillis(value) {
  if (!value) {
    return null;
  }
  if (typeof value.toMillis === 'function') {
    return value.toMillis();
  }
  if (typeof value.toDate === 'function') {
    return value.toDate().getTime();
  }
  if (value instanceof Date) {
    return value.getTime();
  }
  if (typeof value === 'string' || typeof value === 'number') {
    const parsed = new Date(value).getTime();
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

function resolveProviderId(order) {
  return normalizeString(order.assignedProvider) ||
    normalizeString(order.providerId) ||
    normalizeString(order.assignedTechnician && order.assignedTechnician.id) ||
    normalizeString(order.prestador && order.prestador.id);
}

function resolveOrderValue(order) {
  return roundCurrency(
    order.finalPrice ??
      order.totalAmount ??
      order.estimatedPrice ??
      order.price ??
      0
  );
}

function resolveBaseCashbackRate(config, clientData) {
  if (!config || config.enabled !== true) {
    return 0;
  }

  if (config.activePhase && config.activePhase !== 'growth') {
    return 0;
  }

  if (config.tiersEnabled === true) {
    const totalEarned = numberFrom(clientData && clientData.cashbackTotalEarned, 0);
    const silverThreshold = numberFrom(config.silverThreshold, 500);
    const goldThreshold = numberFrom(config.goldThreshold, 1500);
    if (totalEarned >= goldThreshold) {
      return numberFrom(config.goldRate, 8);
    }
    if (totalEarned >= silverThreshold) {
      return numberFrom(config.silverRate, 5);
    }
    return numberFrom(config.bronzeRate, 3);
  }

  return numberFrom(config.earnPercentage, 5);
}

function resolveActiveCampaign(campaignDocs, nowMillis) {
  return campaignDocs
    .map((doc) => ({ id: doc.id, ...doc.data() }))
    .filter((campaign) => {
      if (campaign.enabled !== true) {
        return false;
      }
      const startsAt = toMillis(campaign.startsAt);
      const endsAt = toMillis(campaign.endsAt);
      if (startsAt && startsAt > nowMillis) {
        return false;
      }
      if (endsAt && endsAt < nowMillis) {
        return false;
      }
      return numberFrom(campaign.bonusPercentage, 0) > 0;
    })
    .sort((a, b) => numberFrom(b.bonusPercentage, 0) - numberFrom(a.bonusPercentage, 0))[0] || null;
}

async function loadActiveCampaign(db) {
  const nowMillis = Date.now();
  const snapshot = await db.collection(CAMPAIGNS_COLLECTION).where('enabled', '==', true).get();
  return resolveActiveCampaign(snapshot.docs, nowMillis);
}

async function settleCompletedOrder({ orderId, actorUid, actorType = 'mobile' }) {
  const normalizedOrderId = normalizeString(orderId);
  if (!normalizedOrderId) {
    throw new HttpError(400, 'orderId inválido', { code: 'INVALID_ORDER_ID' });
  }

  const admin = initializeFirebase();
  if (!admin.apps.length) {
    throw new HttpError(500, 'Firebase Admin não inicializado', { code: 'FIREBASE_ADMIN_NOT_INITIALIZED' });
  }

  const db = admin.firestore();
  const fieldValue = admin.firestore.FieldValue;
  const campaign = await loadActiveCampaign(db);

  const result = await db.runTransaction(async (tx) => {
    const orderRef = db.collection(ORDERS_COLLECTION).doc(normalizedOrderId);
    const settlementRef = db.collection(SETTLEMENTS_COLLECTION).doc(normalizedOrderId);
    const configRef = db.collection(CASHBACK_CONFIG_REF[0]).doc(CASHBACK_CONFIG_REF[1]);

    const orderSnap = await tx.get(orderRef);
    if (!orderSnap.exists) {
      throw new HttpError(404, 'Pedido não encontrado', { code: 'ORDER_NOT_FOUND' });
    }

    const order = orderSnap.data() || {};
    if (order.status !== 'completed') {
      throw new HttpError(409, 'Pedido ainda não está concluído', { code: 'ORDER_NOT_COMPLETED' });
    }

    const clientId = normalizeString(order.clientId);
    const providerId = resolveProviderId(order);
    if (!clientId) {
      throw new HttpError(422, 'Pedido sem cliente vinculado', { code: 'ORDER_MISSING_CLIENT' });
    }
    if (!providerId) {
      throw new HttpError(422, 'Pedido sem prestador vinculado', { code: 'ORDER_MISSING_PROVIDER' });
    }

    if (
      actorType !== 'admin' &&
      actorUid &&
      actorUid !== clientId &&
      actorUid !== providerId
    ) {
      throw new HttpError(403, 'Usuário não autorizado a liquidar este pedido', { code: 'FORBIDDEN_SETTLEMENT' });
    }

    const settlementSnap = await tx.get(settlementRef);
    if (settlementSnap.exists) {
      return {
        alreadySettled: true,
        settlementId: settlementRef.id,
        ...settlementSnap.data()
      };
    }

    const clientRef = db.collection('users').doc(clientId);
    const providerRef = db.collection('providers').doc(providerId);
    const providerUserRef = db.collection('users').doc(providerId);
    const cashbackTxRef = clientRef.collection('cashback_transactions').doc(`earn_${normalizedOrderId}`);
    const legacyCashbackQuery = clientRef
      .collection('cashback_transactions')
      .where('orderId', '==', normalizedOrderId)
      .limit(1);

    const [
      configSnap,
      clientSnap,
      providerSnap,
      providerUserSnap,
      cashbackTxSnap,
      legacyCashbackSnap
    ] = await Promise.all([
      tx.get(configRef),
      tx.get(clientRef),
      tx.get(providerRef),
      tx.get(providerUserRef),
      tx.get(cashbackTxRef),
      tx.get(legacyCashbackQuery)
    ]);

    const orderValue = resolveOrderValue(order);
    const providerCommission = roundCurrency(order.providerCommission);
    const config = configSnap.data() || {};
    const clientData = clientSnap.data() || {};
    const campaignBonusRate = campaign ? numberFrom(campaign.bonusPercentage, 0) : 0;
    const baseRate = resolveBaseCashbackRate(config, clientData);
    const cashbackRate = Math.max(0, baseRate + campaignBonusRate);
    const existingCashbackForOrder = cashbackTxSnap.exists || !legacyCashbackSnap.empty;
    let cashbackAmount = 0;

    if (!existingCashbackForOrder && orderValue > 0 && cashbackRate > 0 && config.enabled === true) {
      cashbackAmount = roundCurrency(orderValue * (cashbackRate / 100));
      const maxPerOrder = numberFrom(campaign && campaign.maxCashbackPerOrder, 0);
      if (maxPerOrder > 0) {
        cashbackAmount = Math.min(cashbackAmount, roundCurrency(maxPerOrder));
      }
    }

    const currentBalance = numberFrom(clientData.cashbackBalance, 0);
    const newCashbackBalance = roundCurrency(currentBalance + cashbackAmount);
    const now = fieldValue.serverTimestamp();

    if (providerCommission > 0) {
      const providerFinancialUpdate = {
        providerBalance: fieldValue.increment(providerCommission),
        providerTotalEarned: fieldValue.increment(providerCommission),
        totalEarnings: fieldValue.increment(providerCommission),
        completedJobs: fieldValue.increment(1),
        updatedAt: now
      };

      if (providerSnap.exists) {
        tx.set(providerRef, providerFinancialUpdate, { merge: true });
      }
      if (providerUserSnap.exists) {
        tx.set(providerUserRef, providerFinancialUpdate, { merge: true });
      }
    }

    if (cashbackAmount > 0) {
      tx.set(clientRef, {
        cashbackBalance: newCashbackBalance,
        cashbackTotalEarned: fieldValue.increment(cashbackAmount),
        updatedAt: now
      }, { merge: true });

      tx.set(cashbackTxRef, {
        id: cashbackTxRef.id,
        orderId: normalizedOrderId,
        type: 'earn',
        amount: cashbackAmount,
        earnPercentage: cashbackRate,
        basePercentage: baseRate,
        campaignBonusPercentage: campaignBonusRate,
        campaignId: campaign ? campaign.id : null,
        campaignName: campaign ? campaign.name || null : null,
        orderValue,
        description: campaign
          ? `Cashback do serviço + campanha ${campaign.name || campaign.id}`
          : 'Cashback do serviço concluído',
        balanceAfter: newCashbackBalance,
        createdAt: now
      }, { merge: false });
    }

    const settlementData = {
      orderId: normalizedOrderId,
      clientId,
      providerId,
      orderValue,
      providerCommission,
      cashbackAmount,
      cashbackRate,
      campaignId: campaign ? campaign.id : null,
      campaignName: campaign ? campaign.name || null : null,
      cashbackAlreadyExisted: existingCashbackForOrder,
      status: 'settled',
      actorType,
      actorUid: actorUid || null,
      createdAt: now,
      updatedAt: now
    };

    tx.set(settlementRef, settlementData, { merge: false });
    tx.set(orderRef, {
      settlementStatus: 'settled',
      settlementId: settlementRef.id,
      settledAt: now,
      settledProviderCommission: providerCommission,
      settledCashbackAmount: cashbackAmount,
      updatedAt: now
    }, { merge: true });

    return {
      alreadySettled: false,
      settlementId: settlementRef.id,
      ...settlementData
    };
  });

  logger.info('Pedido liquidado', {
    orderId: normalizedOrderId,
    alreadySettled: result.alreadySettled,
    providerCommission: result.providerCommission,
    cashbackAmount: result.cashbackAmount
  });

  return result;
}

module.exports = {
  settleCompletedOrder
};
