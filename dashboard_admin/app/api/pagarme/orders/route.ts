import { NextRequest, NextResponse } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { pagarmeService } from '@/lib/services/pagarme-service'
import { PagarmeFirebaseSync } from '@/lib/services/pagarme-firebase-sync'

function emptyOrdersPayload(warning: string) {
  return {
    success: true,
    data: [],
    paging: { total: 0, page: 1 },
    source: 'empty',
    warning,
  }
}

/**
 * GET /api/pagarme/orders
 * Lista pedidos com fallback quando a chave privada não está configurada.
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const hasPrivateKey = Boolean(process.env.API_KEY_PRIVATE_PAGARME?.trim())
    if (!hasPrivateKey) {
      return NextResponse.json(emptyOrdersPayload('API_KEY_PRIVATE_PAGARME não configurada'))
    }

    const { searchParams } = new URL(request.url)

    const query = {
      page: searchParams.get('page') ? parseInt(searchParams.get('page')!, 10) : undefined,
      size: searchParams.get('size') ? parseInt(searchParams.get('size')!, 10) : undefined,
      code: searchParams.get('code') || undefined,
      status: searchParams.get('status') || undefined,
      customer_id: searchParams.get('customer_id') || undefined,
      created_since: searchParams.get('created_since') || undefined,
      created_until: searchParams.get('created_until') || undefined,
    }

    const response = await pagarmeService.listOrders(query)

    if (response.errors) {
      return NextResponse.json(
        emptyOrdersPayload(response.errors[0]?.message || 'Erro ao listar pedidos')
      )
    }

    return NextResponse.json({
      success: true,
      data: response.data || [],
      paging: response.paging || { total: response.data?.length || 0, page: query.page || 1 },
      source: 'pagarme',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao listar pedidos:', error)
    return NextResponse.json(
      emptyOrdersPayload('Erro ao listar pedidos')
    )
  }
}

/**
 * POST /api/pagarme/orders
 * Cria um novo pedido
 */
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()

    if (!body.customer || !body.items || !body.payments) {
      return NextResponse.json(
        {
          success: false,
          error: 'Dados obrigatórios não fornecidos (customer, items, payments)',
        },
        { status: 400 }
      )
    }

    const response = await pagarmeService.createOrder(body)

    if (response.errors) {
      return NextResponse.json(
        {
          success: false,
          errors: response.errors,
        },
        { status: 400 }
      )
    }

    let warning: string | undefined

    if (response.data) {
      try {
        await PagarmeFirebaseSync.saveOrder(response.data)
        await PagarmeFirebaseSync.logSync('order_created', 1, 'success')
      } catch (syncError) {
        console.error('Erro ao sincronizar com Firebase:', syncError)
        warning = 'Pedido criado no Pagar.me, mas a sincronizacao com o Firebase falhou.'
      }
    }

    return NextResponse.json(
      {
        success: true,
        data: response.data,
        message: 'Pedido criado com sucesso',
        warning,
      },
      { status: 201 }
    )
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao criar pedido:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao criar pedido',
      },
      { status: 500 }
    )
  }
}
