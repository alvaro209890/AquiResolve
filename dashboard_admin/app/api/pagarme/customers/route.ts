import { NextRequest, NextResponse } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { pagarmeService } from '@/lib/services/pagarme-service'

/**
 * GET /api/pagarme/customers
 * Lista todos os clientes
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const { searchParams } = new URL(request.url)
    
    const query = {
      page: searchParams.get('page') ? parseInt(searchParams.get('page')!) : undefined,
      size: searchParams.get('size') ? parseInt(searchParams.get('size')!) : undefined,
      code: searchParams.get('code') || undefined,
      created_since: searchParams.get('created_since') || undefined,
      created_until: searchParams.get('created_until') || undefined,
    }

    const response = await pagarmeService.listCustomers(query)

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
    console.error('Erro ao listar clientes:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao listar clientes',
      },
      { status: 500 }
    )
  }
}

/**
 * POST /api/pagarme/customers
 * Cria um novo cliente
 */
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()

    // Validação básica
    if (!body.name || !body.email) {
      return NextResponse.json(
        {
          success: false,
          error: 'Dados obrigatórios não fornecidos (name, email)',
        },
        { status: 400 }
      )
    }

    const response = await pagarmeService.createCustomer(body)

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
        message: 'Cliente criado com sucesso',
      },
      { status: 201 }
    )
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao criar cliente:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao criar cliente',
      },
      { status: 500 }
    )
  }
}
