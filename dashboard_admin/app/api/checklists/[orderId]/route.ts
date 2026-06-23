import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/checklists/[orderId] — lê checklist + fotos de uma OS
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ orderId: string }> }
) {
  try {
    await requireAdminPermission(request, 'gestaoPedidos')
    const db = getAdminFirestore()
    const { orderId } = await params

    const [checklistSnap, orderSnap] = await Promise.all([
      db.collection('checklists').doc(orderId).get(),
      db.collection('orders').doc(orderId).get(),
    ])

    if (!checklistSnap.exists && !orderSnap.exists) {
      return NextResponse.json(
        { success: false, error: 'OS não encontrada' },
        { status: 404 }
      )
    }

    return NextResponse.json({
      success: true,
      checklist: checklistSnap.exists ? { id: checklistSnap.id, ...checklistSnap.data() } : null,
      order: orderSnap.exists ? { id: orderSnap.id, ...orderSnap.data() } : null,
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
