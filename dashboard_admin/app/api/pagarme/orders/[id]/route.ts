import { NextRequest, NextResponse } from 'next/server'
import { pagarmeService } from '@/lib/services/pagarme-service'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

/**
 * GET /api/pagarme/orders/:id
 * Busca um pedido por ID
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    await requireAdminPermission(request, 'financeiro')
    const response = await pagarmeService.getOrder(id)

    if (response.errors) {
      return NextResponse.json(
        {
          success: false,
          errors: response.errors,
        },
        { status: 404 }
      )
    }

    return NextResponse.json({
      success: true,
      data: response.data,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar pedido:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao buscar pedido',
      },
      { status: 500 }
    )
  }
}

/**
 * DELETE /api/pagarme/orders/:id
 * Cancela um pedido
 */
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const response = await pagarmeService.cancelOrder(id)

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
      message: 'Pedido cancelado com sucesso',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao cancelar pedido:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao cancelar pedido',
      },
      { status: 500 }
    )
  }
}
