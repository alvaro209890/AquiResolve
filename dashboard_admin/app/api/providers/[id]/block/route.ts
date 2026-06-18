import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, getAdminAuth } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

// POST /api/providers/[id]/block — suspende ou bloqueia definitivamente um prestador.
// Body: { blockType: 'suspension' | 'permanent', reason: string, blockedUntil?: ISOString }
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const db = getAdminFirestore()
    const authAdmin = getAdminAuth()
    const { id } = await params
    const providerId = id
    const body = await request.json()
    const { blockType, reason, blockedUntil } = body as {
      blockType?: 'suspension' | 'permanent'
      reason?: string
      blockedUntil?: string
    }

    if (!reason?.trim()) {
      return NextResponse.json(
        { success: false, error: 'Motivo do bloqueio é obrigatório' },
        { status: 400 }
      )
    }

    const type = blockType === 'suspension' ? 'suspension' : 'permanent'
    const now = admin.firestore.FieldValue.serverTimestamp()

    const blockFields: Record<string, unknown> = {
      blocked: true,
      ativo: false,
      isActive: false,
      status: 'offline',
      blockType: type,
      blockedReason: reason,
      blockedAt: now,
      blockedUntil: type === 'suspension' && blockedUntil ? new Date(blockedUntil) : null,
      updatedAt: now,
      ultimaAtualizacao: now,
    }

    // Atualiza o doc do prestador (afeta o matching/distribuição)
    const providerRef = db.collection('providers').doc(providerId)
    const providerSnap = await providerRef.get()
    if (providerSnap.exists) {
      await providerRef.update(blockFields)
    }

    // Atualiza o doc de usuário correspondente (prestador loga com o mesmo Auth)
    const userRef = db.collection('users').doc(providerId)
    const userSnap = await userRef.get()
    if (userSnap.exists) {
      await userRef.update({
        blocked: true,
        ativo: false,
        isActive: false,
        blockType: type,
        blockedReason: reason,
        blockedAt: now,
        blockedUntil: type === 'suspension' && blockedUntil ? new Date(blockedUntil) : null,
        updatedAt: now,
      })
    }

    // Desabilita a conta no Firebase Auth (impede login)
    try {
      await authAdmin.updateUser(providerId, { disabled: true })
    } catch (authErr) {
      console.warn('Não foi possível desabilitar Auth do prestador:', authErr)
    }

    await db.collection('adminLogs').add({
      action: type === 'suspension' ? 'suspend_provider' : 'block_provider',
      targetId: providerId,
      targetType: 'provider',
      payload: { blockType: type, reason, blockedUntil: blockedUntil ?? null },
      createdAt: now,
    })

    return NextResponse.json({
      success: true,
      providerId,
      blockType: type,
      message: type === 'suspension' ? 'Prestador suspenso' : 'Prestador bloqueado definitivamente',
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao bloquear prestador:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/providers/[id]/block — desbloqueia o prestador
export async function DELETE(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const db = getAdminFirestore()
    const authAdmin = getAdminAuth()
    const { id } = await params
    const providerId = id
    const now = admin.firestore.FieldValue.serverTimestamp()

    const unblockFields = {
      blocked: false,
      ativo: true,
      isActive: true,
      blockType: null,
      blockedReason: null,
      blockedAt: null,
      blockedUntil: null,
      updatedAt: now,
    }

    const providerRef = db.collection('providers').doc(providerId)
    if ((await providerRef.get()).exists) {
      await providerRef.update({ ...unblockFields, ultimaAtualizacao: now })
    }

    const userRef = db.collection('users').doc(providerId)
    if ((await userRef.get()).exists) {
      await userRef.update(unblockFields)
    }

    try {
      await authAdmin.updateUser(providerId, { disabled: false })
    } catch (authErr) {
      console.warn('Não foi possível reabilitar Auth do prestador:', authErr)
    }

    await db.collection('adminLogs').add({
      action: 'unblock_provider',
      targetId: providerId,
      targetType: 'provider',
      payload: {},
      createdAt: now,
    })

    return NextResponse.json({ success: true, providerId, message: 'Prestador desbloqueado' })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao desbloquear prestador:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
