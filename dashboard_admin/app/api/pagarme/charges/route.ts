import { NextRequest, NextResponse } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { pagarmeService } from '@/lib/services/pagarme-service'

function emptyChargesPayload(warning: string) {
  return {
    success: true,
    data: [],
    paging: { total: 0, page: 1 },
    source: 'empty',
    warning,
  }
}

/**
 * GET /api/pagarme/charges
 * Lista cobranças com fallback quando a chave privada não está configurada.
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const hasPrivateKey = Boolean(process.env.API_KEY_PRIVATE_PAGARME?.trim())
    if (!hasPrivateKey) {
      return NextResponse.json(emptyChargesPayload('API_KEY_PRIVATE_PAGARME não configurada'))
    }

    const { searchParams } = new URL(request.url)

    const query = {
      page: searchParams.get('page') ? parseInt(searchParams.get('page')!, 10) : undefined,
      size: searchParams.get('size') ? parseInt(searchParams.get('size')!, 10) : undefined,
      status: searchParams.get('status') || undefined,
      customer_id: searchParams.get('customer_id') || undefined,
      created_since: searchParams.get('created_since') || undefined,
      created_until: searchParams.get('created_until') || undefined,
    }

    const response = await pagarmeService.listCharges(query)

    if (response.errors) {
      return NextResponse.json(
        emptyChargesPayload(response.errors[0]?.message || 'Erro ao listar cobranças')
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
    console.error('Erro ao listar cobranças:', error)
    return NextResponse.json(
      emptyChargesPayload('Erro ao listar cobranças')
    )
  }
}
