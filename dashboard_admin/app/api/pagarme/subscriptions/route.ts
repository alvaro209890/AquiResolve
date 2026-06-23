import { NextRequest, NextResponse } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { pagarmeService } from '@/lib/services/pagarme-service'

/**
 * GET /api/pagarme/subscriptions
 * Lista todas as assinaturas
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const { searchParams } = new URL(request.url)
    
    const query = {
      page: searchParams.get('page') ? parseInt(searchParams.get('page')!) : undefined,
      size: searchParams.get('size') ? parseInt(searchParams.get('size')!) : undefined,
      code: searchParams.get('code') || undefined,
      status: searchParams.get('status') || undefined,
      customer_id: searchParams.get('customer_id') || undefined,
      created_since: searchParams.get('created_since') || undefined,
      created_until: searchParams.get('created_until') || undefined,
    }

    const response = await pagarmeService.listSubscriptions(query)

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
      paging: response.paging,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao listar assinaturas:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao listar assinaturas',
      },
      { status: 500 }
    )
  }
}

/**
 * POST /api/pagarme/subscriptions
 * Cria uma nova assinatura
 */
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()

    // Validação básica
    if (!body.customer || !body.items || !body.payment_method) {
      return NextResponse.json(
        {
          success: false,
          error: 'Dados obrigatórios não fornecidos (customer, items, payment_method)',
        },
        { status: 400 }
      )
    }

    const response = await pagarmeService.createSubscription(body)

    if (response.errors) {
      return NextResponse.json(
        {
          success: false,
          errors: response.errors,
        },
        { status: 400 }
      )
    }

    return NextResponse.json(
      {
        success: true,
        data: response.data,
        message: 'Assinatura criada com sucesso',
      },
      { status: 201 }
    )
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao criar assinatura:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao criar assinatura',
      },
      { status: 500 }
    )
  }
}
