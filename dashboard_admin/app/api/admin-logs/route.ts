import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import {
  adminAuthorizationResponse,
  authorizeAdminRequest,
  requireAdminPermission,
} from '@/lib/server/admin-authorization'

export interface AdminLogEntry {
  action: string          // 'verify_provider' | 'block_user' | 'unblock_user' | 'cancel_order' | 'redirect_order' | 'send_notification'
  targetId: string        // ID do recurso afetado
  targetType: string      // 'provider' | 'user' | 'order'
  adminId?: string        // UID do admin (opcional — extraído do token quando disponível)
  payload?: Record<string, unknown>
  note?: string
}

// POST /api/admin-logs — grava uma ação do admin
export async function POST(request: NextRequest) {
  try {
    const actor = await authorizeAdminRequest(request)
    const db = getAdminFirestore()
    const body = await request.json() as AdminLogEntry

    if (!body.action || !body.targetId || !body.targetType) {
      return NextResponse.json(
        { success: false, error: 'action, targetId e targetType são obrigatórios' },
        { status: 400 }
      )
    }

    const ref = await db.collection('adminLogs').add({
      action: body.action,
      targetId: body.targetId,
      targetType: body.targetType,
      adminId: actor.uid,
      adminEmail: actor.email,
      payload: body.payload ?? null,
      note: body.note ?? null,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    })

    return NextResponse.json({ success: true, id: ref.id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// GET /api/admin-logs — lista os últimos 100 logs
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'controle')
    const db = getAdminFirestore()
    const { searchParams } = new URL(request.url)
    const limitN = Math.min(parseInt(searchParams.get('limit') ?? '100'), 200)
    const action = searchParams.get('action')
    const targetType = searchParams.get('targetType')

    let q = db.collection('adminLogs').orderBy('createdAt', 'desc').limit(limitN)
    if (action) q = q.where('action', '==', action) as typeof q
    if (targetType) q = q.where('targetType', '==', targetType) as typeof q

    const snap = await q.get()
    const logs = snap.docs.map(d => {
      const data = d.data()
      return {
        id: d.id,
        ...data,
        createdAt: data.createdAt?.toDate?.()?.toISOString() ?? null,
      }
    })

    return NextResponse.json({ success: true, logs, total: logs.length })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
