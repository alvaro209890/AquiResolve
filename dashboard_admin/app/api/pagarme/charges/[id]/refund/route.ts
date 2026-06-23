import { NextRequest, NextResponse } from 'next/server'
import { pagarmeService } from '@/lib/services/pagarme-service'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

/**
 * POST /api/pagarme/charges/:id/refund
 * Reembolsa uma cobrança
 */
export async function POST(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()
    const amount = body.amount // Opcional: valor parcial do reembolso

    const response = await pagarmeService.refundCharge(id, amount)

    if (response.errors) {
      return NextResponse.json(
        {
          success: false,
          errors: response.errors,
        },
        { status: 400 }
      )
    }

    return NextResponse.json({
      success: true,
      data: response.data,
      message: 'Cobrança reembolsada com sucesso',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao reembolsar cobrança:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao reembolsar cobrança',
      },
      { status: 500 }
    )
  }
}
