import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

interface RouteCtx {
  params: Promise<{ clientId: string }>
}

// PATCH /api/client-chats/[clientId]
// Body: { pinned?: boolean, archived?: boolean }
export async function PATCH(request: NextRequest, ctx: RouteCtx) {
  try {
    await requireAdminPermission(request, 'controle')
    const { clientId } = await ctx.params
    const body = (await request.json()) as { pinned?: boolean; archived?: boolean }
    const db = getAdminFirestore()

    const update: Record<string, unknown> = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }
    if (typeof body.pinned === 'boolean') update.pinned = body.pinned
    if (typeof body.archived === 'boolean') update.archived = body.archived

    if (Object.keys(update).length === 1) {
      return NextResponse.json(
        { success: false, error: 'informe pinned e/ou archived' },
        { status: 400 }
      )
    }

    await db.collection('client_chats').doc(clientId).set(update, { merge: true })
    return NextResponse.json({ success: true })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
