import { NextRequest, NextResponse } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { pagarmeService } from '@/lib/services/pagarme-service'
import type { PagarmeBalance } from '@/types/pagarme'

function emptyBalancePayload(warning: string) {
  const data: PagarmeBalance = {
    available_amount: 0,
    waiting_funds_amount: 0,
    transferred_amount: 0,
    currency: 'BRL',
  }

  return {
    success: true,
    data,
    source: 'empty',
    warning,
  }
}

/**
 * GET /api/pagarme/balance
 * Retorna o saldo da conta ou payload vazio com aviso.
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const hasPrivateKey = Boolean(process.env.API_KEY_PRIVATE_PAGARME?.trim())
    if (!hasPrivateKey) {
      return NextResponse.json(emptyBalancePayload('API_KEY_PRIVATE_PAGARME não configurada'))
    }

    const response = await pagarmeService.getBalance()

    if (response.errors) {
      return NextResponse.json(
        emptyBalancePayload(response.errors[0]?.message || 'Erro ao buscar saldo')
      )
    }

    return NextResponse.json({
      success: true,
      data: response.data,
      source: 'pagarme',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar saldo:', error)
    return NextResponse.json(emptyBalancePayload('Erro ao buscar saldo'))
  }
}
