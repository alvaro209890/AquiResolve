import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

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
