import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, adminApp } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { resolveUserFcmToken } from '@/lib/server/fcm-token'

async function notify(
  db: admin.firestore.Firestore,
  userId: string,
  title: string,
  body: string,
  type: string,
) {
  try {
    const fcmToken = await resolveUserFcmToken(db, userId)
    if (fcmToken && adminApp) {
      await admin
        .messaging(adminApp)
        .send({ token: fcmToken, notification: { title, body }, data: { type } })
        .catch(() => null)
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

// POST /api/orders/[id]/refund/reject — recusa a solicitação de reembolso do cliente.
// Body: { reason: string } (motivo da recusa, obrigatório). Volta ao cliente no app.
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> },
) {
  try {
    const actor = await requireAdminPermission(request, 'operarFinanceiro')
    const db = getAdminFirestore()
    const { id: orderId } = await params

    let body: { reason?: string } = {}
    try {
      body = await request.json()
    } catch {
      body = {}
    }
    const reason = String(body.reason ?? '').trim()
    if (reason.length < 3) {
      return NextResponse.json(
        { success: false, error: 'Informe o motivo da recusa' },
        { status: 400 },
      )
    }

    const orderRef = db.collection('orders').doc(orderId)
    const snap = await orderRef.get()
    if (!snap.exists) {
      return NextResponse.json({ success: false, error: 'Pedido não encontrado' }, { status: 404 })
    }
    const order = snap.data() || {}
    const current = String(order.refundStatus || '').toLowerCase()
    if (current !== 'requested' && current !== 'pending') {
      return NextResponse.json(
        { success: false, error: 'Não há solicitação de reembolso pendente para recusar' },
        { status: 409 },
      )
    }

    await orderRef.update({
      refundStatus: 'rejected',
      refundRejectionReason: reason,
      refundReviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      refundReviewedBy: actor.uid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    const clientId = order.clientId as string | undefined
    const svcName = (order.serviceName || order.serviceType || 'Serviço') as string
    if (clientId) {
      await notify(
        db,
        clientId,
        'Reembolso recusado',
        `Sua solicitação de reembolso do ${svcName} foi recusada: ${reason}`,
        'payment',
      )
    }

    await db.collection('adminLogs').add({
      action: 'reject_refund',
      targetId: orderId,
      targetType: 'order',
      payload: { reason },
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      actorUid: actor.uid,
      actorEmail: actor.email,
    })

    return NextResponse.json({ success: true, orderId, message: 'Solicitação recusada' })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao recusar reembolso:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
