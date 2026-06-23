import { NextRequest, NextResponse } from 'next/server'
import { settleCompletedOrderAdmin } from '@/lib/services/order-settlement-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// POST /api/orders/[id]/settle — reprocessa a liquidação financeira idempotente da OS
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const { id } = await params
    const settlement = await settleCompletedOrderAdmin(id)
    return NextResponse.json({
      success: true,
      settlement,
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao liquidar pedido:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
