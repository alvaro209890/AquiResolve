import { NextRequest, NextResponse } from 'next/server'
import { settleCompletedOrderAdmin } from '@/lib/services/order-settlement-admin'

// POST /api/orders/[id]/settle — reprocessa a liquidação financeira idempotente da OS
export async function POST(
  _request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params
    const settlement = await settleCompletedOrderAdmin(id)
    return NextResponse.json({
      success: true,
      settlement,
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao liquidar pedido:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
