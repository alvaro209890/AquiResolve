import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

interface RouteCtx {
  params: Promise<{ providerId: string }>
}

// PATCH /api/provider-chats/[providerId]/read?role=admin|provider
// Zera o contador de unread do papel informado.
export async function PATCH(request: NextRequest, ctx: RouteCtx) {
  try {
    await requireAdminPermission(request, 'controle')
    const { providerId } = await ctx.params
    const { searchParams } = new URL(request.url)
    const role = searchParams.get('role') ?? 'admin'
    const db = getAdminFirestore()

    const field = role === 'provider' ? 'unreadByProvider' : 'unreadByAdmin'
    await db.collection('provider_chats').doc(providerId).set(
      {
        [field]: 0,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    )

    return NextResponse.json({ success: true })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
