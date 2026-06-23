import { NextRequest, NextResponse } from 'next/server'
import { pagarmeService } from '@/lib/services/pagarme-service'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

/**
 * GET /api/pagarme/subscriptions/:id
 * Busca uma assinatura por ID
 */
export async function GET(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    await requireAdminPermission(request, 'financeiro')
    const response = await pagarmeService.getSubscription(id)

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
    console.error('Erro ao buscar assinatura:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao buscar assinatura',
      },
      { status: 500 }
    )
  }
}

/**
 * PUT /api/pagarme/subscriptions/:id
 * Atualiza uma assinatura
 */
export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()
    const response = await pagarmeService.updateSubscription(id, body)

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
      message: 'Assinatura atualizada com sucesso',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao atualizar assinatura:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao atualizar assinatura',
      },
      { status: 500 }
    )
  }
}

/**
 * DELETE /api/pagarme/subscriptions/:id
 * Cancela uma assinatura
 */
export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const response = await pagarmeService.cancelSubscription(id)

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
      message: 'Assinatura cancelada com sucesso',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao cancelar assinatura:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao cancelar assinatura',
      },
      { status: 500 }
    )
  }
}
