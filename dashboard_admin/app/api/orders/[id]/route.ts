import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, adminApp } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

async function pushAndPersist(
  db: admin.firestore.Firestore,
  userId: string,
  title: string,
  body: string,
  type: string
) {
  try {
    const tokenSnap = await db.collection('userTokens').doc(userId).get()
    const fcmToken = tokenSnap.data()?.token || tokenSnap.data()?.fcmToken
    if (fcmToken && adminApp) {
      await admin.messaging(adminApp).send({ token: fcmToken, notification: { title, body }, data: { type } }).catch(() => null)
    }
    await db.collection('notifications').add({
      userId,
      title,
      message: body,
      isRead: false,
      type,
      timestamp: admin.firestore.FieldValue.serverTimestamp(),
    })
  } catch {
    // notificação não bloqueia a operação principal
  }
}

const VALID_STATUSES = [
  'awaiting_payment',
  'pending',
  'distributing',
  'assigned',
  'in_progress',
  'completed',
  'cancelled',
]

// GET /api/orders/[id] — retorna um pedido pelo ID
export async function GET(
  _request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const db = getAdminFirestore()
    const snap = await db.collection('orders').doc(params.id).get()
    if (!snap.exists) {
      return NextResponse.json({ success: false, error: 'Pedido não encontrado' }, { status: 404 })
    }
    return NextResponse.json({ success: true, order: { id: snap.id, ...snap.data() } })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// PATCH /api/orders/[id] — atualiza status ou outros campos de um pedido (Admin SDK)
export async function PATCH(
  request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const db = getAdminFirestore()
    const orderId = params.id
    const body = await request.json()

    const {
      status,
      cancelledBy,
      cancellationReason,
      adminNote,
      assignedProvider,
      assignedProviderName,
    } = body as Record<string, string | undefined>

    const orderRef = db.collection('orders').doc(orderId)
    const orderSnap = await orderRef.get()
    if (!orderSnap.exists) {
      return NextResponse.json({ success: false, error: 'Pedido não encontrado' }, { status: 404 })
    }

    const updateData: Record<string, unknown> = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    if (status) {
      if (!VALID_STATUSES.includes(status)) {
        return NextResponse.json(
          { success: false, error: `Status inválido. Válidos: ${VALID_STATUSES.join(', ')}` },
          { status: 400 }
        )
      }
      updateData.status = status

      if (status === 'cancelled') {
        updateData.cancelledAt = admin.firestore.FieldValue.serverTimestamp()
        updateData.cancelledBy = cancelledBy || 'admin'
        if (cancellationReason) updateData.cancellationReason = cancellationReason
      }

      if (status === 'completed') {
        updateData.completedAt = admin.firestore.FieldValue.serverTimestamp()

        const orderSnap2 = await orderRef.get()
        const orderData = orderSnap2.data()
        const providerId = orderData?.assignedProvider
        const commission = orderData?.providerCommission ?? 0
        const clientId = orderData?.clientId
        const estimatedPrice = Number(orderData?.estimatedPrice ?? 0)

        // Acumula saldo do prestador
        if (providerId && commission > 0) {
          const provRef = db.collection('providers').doc(providerId)
          const userRef2 = db.collection('users').doc(providerId)
          const [provSnap, userSnap2] = await Promise.all([provRef.get(), userRef2.get()])
          if (provSnap.exists) {
            await provRef.update({
              providerBalance: admin.firestore.FieldValue.increment(commission),
              providerTotalEarned: admin.firestore.FieldValue.increment(commission),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            })
          }
          if (userSnap2.exists) {
            await userRef2.update({
              providerBalance: admin.firestore.FieldValue.increment(commission),
              providerTotalEarned: admin.firestore.FieldValue.increment(commission),
              updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            })
          }
        }

        // Credita cashback ao cliente
        if (clientId && estimatedPrice > 0) {
          const cashbackConfigSnap = await db.collection('app_config').doc('cashback').get()
          const cashbackCfg = cashbackConfigSnap.data()
          if (cashbackCfg?.enabled === true) {
            const earnPct = Number(cashbackCfg.earnPercentage ?? 5)
            const cashbackAmount = Math.round((estimatedPrice * earnPct) / 100 * 100) / 100
            if (cashbackAmount > 0) {
              const clientRef = db.collection('users').doc(clientId)
              await clientRef.update({
                cashbackBalance: admin.firestore.FieldValue.increment(cashbackAmount),
                cashbackTotalEarned: admin.firestore.FieldValue.increment(cashbackAmount),
                updatedAt: admin.firestore.FieldValue.serverTimestamp(),
              })
              await clientRef.collection('cashback_transactions').add({
                orderId,
                amount: cashbackAmount,
                earnPercentage: earnPct,
                orderValue: estimatedPrice,
                type: 'earned',
                createdAt: admin.firestore.FieldValue.serverTimestamp(),
              })
            }
          }
        }
      }

      if (status === 'distributing') {
        updateData.distributingAt = admin.firestore.FieldValue.serverTimestamp()
      }
    }

    if (adminNote) {
      updateData.adminNote = adminNote
    }

    if (assignedProvider) {
      updateData.assignedProvider = assignedProvider
      if (assignedProviderName) updateData.assignedProviderName = assignedProviderName
      updateData.assignedAt = admin.firestore.FieldValue.serverTimestamp()
    }

    await orderRef.update(updateData)

    // Auto-notificações por mudança de status
    if (status) {
      const freshSnap = await orderRef.get()
      const freshData = freshSnap.data()
      const clientId = freshData?.clientId as string | undefined
      const providerId = freshData?.assignedProvider as string | undefined
      const svcName = (freshData?.serviceName || freshData?.serviceType || 'Serviço') as string

      if (status === 'assigned' && clientId) {
        await pushAndPersist(db, clientId, 'Prestador a caminho!', `Seu ${svcName} foi atribuído e o prestador está a caminho.`, 'order')
      } else if (status === 'in_progress' && clientId) {
        await pushAndPersist(db, clientId, 'Serviço iniciado', `Seu ${svcName} está em andamento.`, 'order')
      } else if (status === 'completed') {
        if (clientId) await pushAndPersist(db, clientId, 'Serviço concluído!', `Seu ${svcName} foi concluído. Avalie o prestador.`, 'order')
        if (providerId) await pushAndPersist(db, providerId, 'Serviço finalizado', `O ${svcName} foi concluído. Seu saldo foi atualizado.`, 'payment')
      } else if (status === 'cancelled') {
        if (clientId) await pushAndPersist(db, clientId, 'Pedido cancelado', `Seu pedido de ${svcName} foi cancelado.`, 'order')
        if (providerId) await pushAndPersist(db, providerId, 'Atendimento cancelado', `O pedido de ${svcName} foi cancelado pelo administrador.`, 'order')
      }
    }

    // Log de auditoria para cancelamentos
    if (status === 'cancelled') {
      await db.collection('adminLogs').add({
        action: 'cancel_order',
        targetId: orderId,
        targetType: 'order',
        payload: {
          cancellationReason: cancellationReason ?? null,
          cancelledBy: cancelledBy ?? 'admin',
        },
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      })
    }

    return NextResponse.json({
      success: true,
      orderId,
      updated: updateData,
      message: 'Pedido atualizado com sucesso',
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao atualizar pedido:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
