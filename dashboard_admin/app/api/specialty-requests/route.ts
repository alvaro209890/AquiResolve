import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/specialty-requests?status=pending
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'aprovarPrestadores')
    const db = getAdminFirestore()
    const { searchParams } = new URL(request.url)
    const status = searchParams.get('status') ?? 'pending'

    let query = db.collection('provider_specialty_requests')
      .orderBy('createdAt', 'desc')
      .limit(100) as admin.firestore.Query

    if (status !== 'all') {
      query = query.where('status', '==', status)
    }

    const snap = await query.get()
    const requests = snap.docs.map(d => ({ id: d.id, ...d.data() }))

    return NextResponse.json({ success: true, requests })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/specialty-requests
// Body: { requestId, action: 'approve' | 'reject', rejectionReason?: string }
export async function POST(request: NextRequest) {
  try {
    const actor = await requireAdminPermission(request, 'aprovarPrestadores')
    const db = getAdminFirestore()
    const body = await request.json()
    const { requestId, action, rejectionReason } = body as {
      requestId: string
      action: 'approve' | 'reject'
      rejectionReason?: string
    }

    if (!requestId || !['approve', 'reject'].includes(action)) {
      return NextResponse.json(
        { success: false, error: 'Informe requestId e action (approve|reject)' },
        { status: 400 }
      )
    }

    const reqRef = db.collection('provider_specialty_requests').doc(requestId)
    const reqSnap = await reqRef.get()

    if (!reqSnap.exists) {
      return NextResponse.json({ success: false, error: 'Solicitação não encontrada' }, { status: 404 })
    }

    const data = reqSnap.data()!
    if (data.status !== 'pending') {
      return NextResponse.json(
        { success: false, error: `Solicitação já ${data.status === 'approved' ? 'aprovada' : 'rejeitada'}` },
        { status: 409 }
      )
    }

    const providerId: string = data.providerId
    const now = admin.firestore.FieldValue.serverTimestamp()

    if (action === 'approve') {
      // Atualiza providers/{providerId}.services com as especialidades solicitadas
      await db.collection('providers').doc(providerId).set(
        { services: data.requestedServices, updatedAt: now },
        { merge: true }
      )
      await reqRef.update({ status: 'approved', reviewedAt: now })

      // Notifica o prestador
      await db.collection('notifications').add({
        userId: providerId,
        title: 'Especialidades aprovadas!',
        message: `Suas especialidades foram atualizadas: ${(data.requestedServices as string[]).join(', ')}.`,
        type: 'specialty_approved',
        isRead: false,
        timestamp: now,
      })

      // Log de auditoria
      await db.collection('adminLogs').add({
        action: 'approve_specialty_request',
        targetId: providerId,
        targetType: 'provider',
        payload: { requestId, services: data.requestedServices },
        createdAt: now,
        actorUid: actor.uid,
        actorEmail: actor.email,
      })
    } else {
      await reqRef.update({
        status: 'rejected',
        rejectionReason: rejectionReason ?? null,
        reviewedAt: now,
      })

      // Notifica o prestador
      const reason = rejectionReason ? ` Motivo: ${rejectionReason}` : ''
      await db.collection('notifications').add({
        userId: providerId,
        title: 'Solicitação de especialidades recusada',
        message: `Sua solicitação de alteração de especialidades foi recusada.${reason}`,
        type: 'specialty_rejected',
        isRead: false,
        timestamp: now,
      })

      await db.collection('adminLogs').add({
        action: 'reject_specialty_request',
        targetId: providerId,
        targetType: 'provider',
        payload: { requestId, rejectionReason: rejectionReason ?? null },
        createdAt: now,
        actorUid: actor.uid,
        actorEmail: actor.email,
      })
    }

    return NextResponse.json({
      success: true,
      action,
      providerId,
      message: action === 'approve' ? 'Especialidades aprovadas e atualizadas' : 'Solicitação rejeitada',
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao processar solicitação de especialidade:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
