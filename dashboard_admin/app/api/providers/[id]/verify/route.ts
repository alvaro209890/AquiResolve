import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// PATCH /api/providers/[id]/verify — aprova ou rejeita um prestador
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const actor = await requireAdminPermission(request, 'aprovarPrestadores')
    const db = getAdminFirestore()
    const { id } = await params
    const providerId = id

    const body = await request.json()
    const { status, rejectionReason } = body as {
      status: 'approved' | 'rejected' | 'pending'
      rejectionReason?: string
    }

    if (!['approved', 'rejected', 'pending'].includes(status)) {
      return NextResponse.json(
        { success: false, error: 'Status inválido. Use: approved, rejected ou pending' },
        { status: 400 }
      )
    }

    const updateData: Record<string, unknown> = {
      verificationStatus: status,
      verificationUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    if (status === 'rejected' && rejectionReason) {
      updateData.rejectionReason = rejectionReason
    }

    if (status === 'approved') {
      updateData.ativo = true
      updateData.isActive = true
      delete updateData.rejectionReason
    }

    // Atualiza providers/{providerId}
    const providerRef = db.collection('providers').doc(providerId)
    const providerSnap = await providerRef.get()
    if (providerSnap.exists) {
      await providerRef.update(updateData)
    } else {
      await providerRef.set(updateData, { merge: true })
    }

    // Também atualiza users/{providerId}.verificationStatus para consistência
    const userRef = db.collection('users').doc(providerId)
    const userSnap = await userRef.get()
    if (userSnap.exists) {
      await userRef.update({
        verificationStatus: status,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      })
    }

    // Registra no histórico de verificação
    await db.collection('provider_verifications').add({
      providerId,
      status,
      rejectionReason: rejectionReason || null,
      reviewedAt: admin.firestore.FieldValue.serverTimestamp(),
      source: 'admin_panel',
    })

    // Log de auditoria
    await db.collection('adminLogs').add({
      action: 'verify_provider',
      targetId: providerId,
      targetType: 'provider',
      payload: { status, rejectionReason: rejectionReason ?? null },
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      actorUid: actor.uid,
      actorEmail: actor.email,
    })

    return NextResponse.json({
      success: true,
      providerId,
      status,
      message: status === 'approved'
        ? 'Prestador aprovado com sucesso'
        : status === 'rejected'
        ? 'Prestador rejeitado'
        : 'Status atualizado',
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao verificar prestador:', message)
    return NextResponse.json(
      { success: false, error: message },
      { status: 500 }
    )
  }
}

// GET /api/providers/[id]/verify — retorna status de verificação do prestador
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    await requireAdminPermission(request, 'aprovarPrestadores')
    const db = getAdminFirestore()
    const { id } = await params
    const providerId = id

    const [providerSnap, historySnap] = await Promise.all([
      db.collection('providers').doc(providerId).get(),
      db.collection('provider_verifications')
        .where('providerId', '==', providerId)
        .orderBy('reviewedAt', 'desc')
        .limit(10)
        .get(),
    ])

    return NextResponse.json({
      success: true,
      providerId,
      verificationStatus: providerSnap.data()?.verificationStatus ?? 'pending',
      history: historySnap.docs.map(d => ({ id: d.id, ...d.data() })),
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
