import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, getAdminAuth } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

// GET /api/users/[id] — retorna dados de um usuário
export async function GET(
  _request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const db = getAdminFirestore()
    const snap = await db.collection('users').doc(params.id).get()
    if (!snap.exists) {
      return NextResponse.json({ success: false, error: 'Usuário não encontrado' }, { status: 404 })
    }
    return NextResponse.json({ success: true, user: { id: snap.id, ...snap.data() } })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// PATCH /api/users/[id] — atualiza campos de um usuário (Admin SDK — bypassa regras)
export async function PATCH(
  request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const db = getAdminFirestore()
    const authAdmin = getAdminAuth()
    const userId = params.id
    const body = await request.json()

    const {
      isActive,
      blocked,
      blockedReason,
      role,
      verificationStatus,
      adminNote,
    } = body as Record<string, unknown>

    const userRef = db.collection('users').doc(userId)
    const userSnap = await userRef.get()
    if (!userSnap.exists) {
      return NextResponse.json({ success: false, error: 'Usuário não encontrado' }, { status: 404 })
    }

    const updateData: Record<string, unknown> = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    if (isActive !== undefined) {
      updateData.isActive = Boolean(isActive)
      updateData.ativo = Boolean(isActive)
    }

    if (blocked !== undefined) {
      updateData.blocked = Boolean(blocked)
      if (blocked) {
        updateData.blockedAt = admin.firestore.FieldValue.serverTimestamp()
        updateData.blockedReason = blockedReason || 'Bloqueado pelo administrador'
        // Desabilitar no Firebase Auth também
        await authAdmin.updateUser(userId, { disabled: true })
      } else {
        updateData.blockedAt = null
        updateData.blockedReason = null
        await authAdmin.updateUser(userId, { disabled: false })
      }
    }

    if (verificationStatus) {
      updateData.verificationStatus = verificationStatus
    }

    if (adminNote) {
      updateData.adminNote = adminNote
    }

    // Se mudar o role, atualizar custom claims
    if (role) {
      updateData.role = role
      updateData.userType = role
      await authAdmin.setCustomUserClaims(userId, { role })
    }

    await userRef.update(updateData)

    return NextResponse.json({
      success: true,
      userId,
      updated: updateData,
      message: 'Usuário atualizado com sucesso',
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao atualizar usuário:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/users/[id] — bloqueia permanentemente (não apaga por segurança)
export async function DELETE(
  _request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const db = getAdminFirestore()
    const authAdmin = getAdminAuth()
    const userId = params.id

    await authAdmin.updateUser(userId, { disabled: true })
    await db.collection('users').doc(userId).update({
      blocked: true,
      isActive: false,
      ativo: false,
      blockedAt: admin.firestore.FieldValue.serverTimestamp(),
      blockedReason: 'Conta desativada pelo administrador',
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return NextResponse.json({
      success: true,
      userId,
      message: 'Usuário desativado com sucesso (conta bloqueada no Firebase Auth)',
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao desativar usuário:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
