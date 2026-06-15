import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

const ORDERS_COLLECTION = 'orders'
const SETTLEMENTS_COLLECTION = 'order_settlements'
const CAMPAIGNS_COLLECTION = 'cashback_campaigns'

function numberFrom(value: unknown, fallback = 0) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function roundCurrency(value: unknown) {
  return Math.round(numberFrom(value) * 100) / 100
}

function normalizeString(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

function toMillis(value: any): number | null {
  if (!value) return null
  if (typeof value.toMillis === 'function') return value.toMillis()
  if (typeof value.toDate === 'function') return value.toDate().getTime()
  if (value instanceof Date) return value.getTime()
  if (typeof value === 'string' || typeof value === 'number') {
    const parsed = new Date(value).getTime()
    return Number.isFinite(parsed) ? parsed : null
  }
  return null
}

function resolveProviderId(order: Record<string, any>) {
  return normalizeString(order.assignedProvider) ||
    normalizeString(order.providerId) ||
    normalizeString(order.assignedTechnician?.id) ||
    normalizeString(order.prestador?.id)
}

function resolveOrderValue(order: Record<string, any>) {
  return roundCurrency(
    order.finalPrice ??
      order.totalAmount ??
      order.estimatedPrice ??
      order.price ??
      0
  )
}

function resolveBaseCashbackRate(config: Record<string, any>, clientData: Record<string, any>) {
  if (config.enabled !== true) return 0
  if (config.activePhase && config.activePhase !== 'growth') return 0

  if (config.tiersEnabled === true) {
    const totalEarned = numberFrom(clientData.cashbackTotalEarned)
    const silverThreshold = numberFrom(config.silverThreshold, 500)
    const goldThreshold = numberFrom(config.goldThreshold, 1500)
    if (totalEarned >= goldThreshold) return numberFrom(config.goldRate, 8)
    if (totalEarned >= silverThreshold) return numberFrom(config.silverRate, 5)
    return numberFrom(config.bronzeRate, 3)
  }

  return numberFrom(config.earnPercentage, 5)
}

async function loadActiveCampaign(db: admin.firestore.Firestore) {
  const nowMillis = Date.now()
  const snap = await db.collection(CAMPAIGNS_COLLECTION).where('enabled', '==', true).get()
  return snap.docs
    .map(doc => ({ id: doc.id, ...doc.data() }))
    .filter((campaign: any) => {
      const startsAt = toMillis(campaign.startsAt)
      const endsAt = toMillis(campaign.endsAt)
      return campaign.enabled === true &&
        numberFrom(campaign.bonusPercentage) > 0 &&
        (!startsAt || startsAt <= nowMillis) &&
        (!endsAt || endsAt >= nowMillis)
    })
    .sort((a: any, b: any) => numberFrom(b.bonusPercentage) - numberFrom(a.bonusPercentage))[0] || null
}

export async function settleCompletedOrderAdmin(orderId: string, actorUid = 'admin') {
  const normalizedOrderId = normalizeString(orderId)
  if (!normalizedOrderId) {
    throw new Error('orderId inválido')
  }

  const db = getAdminFirestore()
  const campaign = await loadActiveCampaign(db)

  return db.runTransaction(async (tx) => {
    const orderRef = db.collection(ORDERS_COLLECTION).doc(normalizedOrderId)
    const settlementRef = db.collection(SETTLEMENTS_COLLECTION).doc(normalizedOrderId)
    const configRef = db.collection('app_config').doc('cashback')

    const orderSnap = await tx.get(orderRef)
    if (!orderSnap.exists) {
      throw new Error('Pedido não encontrado')
    }

    const order = orderSnap.data() || {}
    if (order.status !== 'completed') {
      throw new Error('Pedido ainda não está concluído')
    }

    const clientId = normalizeString(order.clientId)
    const providerId = resolveProviderId(order)
    if (!clientId) throw new Error('Pedido sem cliente vinculado')
    if (!providerId) throw new Error('Pedido sem prestador vinculado')

    const settlementSnap = await tx.get(settlementRef)
    if (settlementSnap.exists) {
      return {
        alreadySettled: true,
        settlementId: settlementRef.id,
        ...settlementSnap.data(),
      }
    }

    const clientRef = db.collection('users').doc(clientId)
    const providerRef = db.collection('providers').doc(providerId)
    const providerUserRef = db.collection('users').doc(providerId)
    const cashbackTxRef = clientRef.collection('cashback_transactions').doc(`earn_${normalizedOrderId}`)
    const legacyCashbackQuery = clientRef
      .collection('cashback_transactions')
      .where('orderId', '==', normalizedOrderId)
      .limit(1)

    const configSnap = await tx.get(configRef)
    const clientSnap = await tx.get(clientRef)
    const providerSnap = await tx.get(providerRef)
    const providerUserSnap = await tx.get(providerUserRef)
    const cashbackTxSnap = await tx.get(cashbackTxRef)
    const legacyCashbackSnap = await tx.get(legacyCashbackQuery)

    const orderValue = resolveOrderValue(order)
    const providerCommission = roundCurrency(order.providerCommission)
    const config = configSnap.data() || {}
    const clientData = clientSnap.data() || {}
    const campaignBonusRate = campaign ? numberFrom((campaign as any).bonusPercentage) : 0
    const baseRate = resolveBaseCashbackRate(config, clientData)
    const cashbackRate = Math.max(0, baseRate + campaignBonusRate)
    const existingCashbackForOrder = cashbackTxSnap.exists || !legacyCashbackSnap.empty
    let cashbackAmount = 0

    if (!existingCashbackForOrder && orderValue > 0 && cashbackRate > 0 && config.enabled === true) {
      cashbackAmount = roundCurrency(orderValue * (cashbackRate / 100))
      const maxPerOrder = campaign ? numberFrom((campaign as any).maxCashbackPerOrder) : 0
      if (maxPerOrder > 0) {
        cashbackAmount = Math.min(cashbackAmount, roundCurrency(maxPerOrder))
      }
    }

    const now = admin.firestore.FieldValue.serverTimestamp()
    const currentBalance = numberFrom(clientData.cashbackBalance)
    const newCashbackBalance = roundCurrency(currentBalance + cashbackAmount)

    if (providerCommission > 0) {
      const providerFinancialUpdate = {
        providerBalance: admin.firestore.FieldValue.increment(providerCommission),
        providerTotalEarned: admin.firestore.FieldValue.increment(providerCommission),
        totalEarnings: admin.firestore.FieldValue.increment(providerCommission),
        completedJobs: admin.firestore.FieldValue.increment(1),
        updatedAt: now,
      }

      if (providerSnap.exists) tx.set(providerRef, providerFinancialUpdate, { merge: true })
      if (providerUserSnap.exists) tx.set(providerUserRef, providerFinancialUpdate, { merge: true })
    }

    if (cashbackAmount > 0) {
      tx.set(clientRef, {
        cashbackBalance: newCashbackBalance,
        cashbackTotalEarned: admin.firestore.FieldValue.increment(cashbackAmount),
        updatedAt: now,
      }, { merge: true })

      tx.set(cashbackTxRef, {
        id: cashbackTxRef.id,
        orderId: normalizedOrderId,
        type: 'earn',
        amount: cashbackAmount,
        earnPercentage: cashbackRate,
        basePercentage: baseRate,
        campaignBonusPercentage: campaignBonusRate,
        campaignId: campaign ? (campaign as any).id : null,
        campaignName: campaign ? (campaign as any).name || null : null,
        orderValue,
        description: campaign
          ? `Cashback do serviço + campanha ${(campaign as any).name || (campaign as any).id}`
          : 'Cashback do serviço concluído',
        balanceAfter: newCashbackBalance,
        createdAt: now,
      }, { merge: false })
    }

    const settlementData = {
      orderId: normalizedOrderId,
      clientId,
      providerId,
      orderValue,
      providerCommission,
      cashbackAmount,
      cashbackRate,
      campaignId: campaign ? (campaign as any).id : null,
      campaignName: campaign ? (campaign as any).name || null : null,
      cashbackAlreadyExisted: existingCashbackForOrder,
      status: 'settled',
      actorType: 'admin',
      actorUid,
      createdAt: now,
      updatedAt: now,
    }

    tx.set(settlementRef, settlementData, { merge: false })
    tx.set(orderRef, {
      settlementStatus: 'settled',
      settlementId: settlementRef.id,
      settledAt: now,
      settledProviderCommission: providerCommission,
      settledCashbackAmount: cashbackAmount,
      updatedAt: now,
    }, { merge: true })

    return {
      alreadySettled: false,
      settlementId: settlementRef.id,
      ...settlementData,
    }
  })
}
