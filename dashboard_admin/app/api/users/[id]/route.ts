import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, getAdminAuth } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/users/[id] — retorna dados de um usuário
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    await requireAdminPermission(request, 'gestaoUsuarios')
    const { id } = await params
    const db = getAdminFirestore()
    const snap = await db.collection('users').doc(id).get()
    if (!snap.exists) {
      return NextResponse.json({ success: false, error: 'Usuário não encontrado' }, { status: 404 })
    }
    return NextResponse.json({ success: true, user: { id: snap.id, ...snap.data() } })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// PATCH /api/users/[id] — atualiza campos de um usuário (Admin SDK — bypassa regras)
export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const actor = await requireAdminPermission(request, 'administrarUsuarios')
    const db = getAdminFirestore()
    const authAdmin = getAdminAuth()
    const { id } = await params
    const userId = id
    const body = await request.json()

    const {
      isActive,
      blocked,
      blockedReason,
      blockType,
      blockedUntil,
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
        // 'temporary' ou 'permanent' (padrão: permanent)
        updateData.blockType = blockType === 'temporary' ? 'temporary' : 'permanent'
        if (blockType === 'temporary' && blockedUntil) {
          updateData.blockedUntil = new Date(blockedUntil as string)
        } else {
          updateData.blockedUntil = null
        }
        await authAdmin.updateUser(userId, { disabled: true })
      } else {
        updateData.blockedAt = null
        updateData.blockedReason = null
        updateData.blockType = null
        updateData.blockedUntil = null
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

    // Log de auditoria para bloqueio/desbloqueio
    if (blocked !== undefined) {
      await db.collection('adminLogs').add({
        action: blocked ? 'block_user' : 'unblock_user',
        targetId: userId,
        targetType: 'user',
        payload: {
          blocked,
          blockedReason: blocked ? (blockedReason ?? null) : null,
          blockType: blocked ? (blockType === 'temporary' ? 'temporary' : 'permanent') : null,
          blockedUntil: blocked && blockType === 'temporary' ? (blockedUntil ?? null) : null,
        },
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
        actorUid: actor.uid,
        actorEmail: actor.email,
      })
    }

    return NextResponse.json({
      success: true,
      userId,
      updated: updateData,
      message: 'Usuário atualizado com sucesso',
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao atualizar usuário:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/users/[id] — bloqueia permanentemente (não apaga por segurança)
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    await requireAdminPermission(request, 'administrarUsuarios')
    const db = getAdminFirestore()
    const authAdmin = getAdminAuth()
    const { id } = await params
    const userId = id

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
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao desativar usuário:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
