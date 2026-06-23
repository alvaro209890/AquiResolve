import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// POST /api/orders/[id]/redirect — remove prestador atual e reatribui (ou volta a distribuindo)
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const actor = await requireAdminPermission(request, 'operarPedidos')
    const db = getAdminFirestore()
    const { id } = await params
    const orderId = id
    const body = await request.json()
    const { newProviderId, newProviderName, reason } = body as {
      newProviderId?: string
      newProviderName?: string
      reason: string
    }

    if (!reason?.trim()) {
      return NextResponse.json(
        { success: false, error: 'Motivo do redirecionamento é obrigatório' },
        { status: 400 }
      )
    }

    const orderRef = db.collection('orders').doc(orderId)
    const orderSnap = await orderRef.get()
    if (!orderSnap.exists) {
      return NextResponse.json({ success: false, error: 'Pedido não encontrado' }, { status: 404 })
    }

    const order = orderSnap.data()!
    const previousProvider = order.assignedProvider ?? null
    const previousProviderName = order.assignedProviderName ?? null

    const updateData: Record<string, unknown> = {
      previousProvider,
      previousProviderName,
      redirectedAt: admin.firestore.FieldValue.serverTimestamp(),
      redirectReason: reason,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    if (newProviderId && newProviderName) {
      // Reatribuir para novo prestador
      updateData.assignedProvider = newProviderId
      updateData.assignedProviderName = newProviderName
      updateData.status = 'assigned'
      updateData.assignedAt = admin.firestore.FieldValue.serverTimestamp()
    } else {
      // Volta para distribuindo (sem prestador)
      updateData.assignedProvider = null
      updateData.assignedProviderName = null
      updateData.status = 'distributing'
      updateData.assignedAt = null
    }

    await orderRef.update(updateData)

    // Registra histórico de redirecionamento
    await db.collection('orders').doc(orderId).collection('redirectHistory').add({
      previousProvider,
      previousProviderName,
      newProvider: newProviderId ?? null,
      newProviderName: newProviderName ?? null,
      reason,
      redirectedAt: admin.firestore.FieldValue.serverTimestamp(),
      source: 'admin_panel',
      actorUid: actor.uid,
      actorEmail: actor.email,
    })

    return NextResponse.json({
      success: true,
      orderId,
      newStatus: updateData.status,
      message: newProviderId
        ? `Pedido reatribuído para ${newProviderName}`
        : 'Prestador removido — pedido volta para distribuição',
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao redirecionar pedido:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
