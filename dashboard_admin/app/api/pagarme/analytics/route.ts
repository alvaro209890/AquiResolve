import { NextRequest, NextResponse } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { PagarmeService } from '@/lib/services/pagarme-service'
import type { PagarmeAnalytics } from '@/types/pagarme'

function emptyAnalytics(startDate: string, endDate: string): PagarmeAnalytics {
  return {
    total_amount: 0,
    total_orders: 0,
    total_customers: 0,
    total_subscriptions: 0,
    payment_methods: {
      credit_card: 0,
      debit_card: 0,
      pix: 0,
      boleto: 0,
    },
    status_breakdown: {
      paid: 0,
      pending: 0,
      failed: 0,
      canceled: 0,
    },
    period: {
      start: startDate,
      end: endDate,
    },
  }
}

export async function GET(request: NextRequest) {
  const { searchParams } = new URL(request.url)
  const endDate = searchParams.get('end_date') || new Date().toISOString().split('T')[0]
  const startDate =
    searchParams.get('start_date') ||
    new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0]

  try {
    await requireAdminPermission(request, 'financeiro')
    const hasPrivateKey = Boolean(process.env.API_KEY_PRIVATE_PAGARME?.trim())
    if (!hasPrivateKey) {
      const analytics = emptyAnalytics(startDate, endDate)
      return NextResponse.json({
        success: true,
        data: analytics,
        totalRevenue: analytics.total_amount,
        totalTransactions: analytics.total_orders,
        successRate: 0,
        conversionRate: 0,
        paymentMethods: analytics.payment_methods,
        recentCharges: [],
        source: 'empty',
        warning: 'API_KEY_PRIVATE_PAGARME não configurada',
      })
    }

    const pagarmeService = new PagarmeService()
    const analytics = await pagarmeService.getAnalytics(startDate, endDate)

    const chargesResponse = await pagarmeService.listCharges({
      page: 1,
      size: 10,
      created_since: startDate,
      created_until: endDate,
    })

    const totalTransactions = analytics.total_orders || 0
    const paidTransactions = analytics.status_breakdown?.paid || 0
    const successRate = totalTransactions > 0 ? (paidTransactions / totalTransactions) * 100 : 0

    return NextResponse.json({
      success: true,
      data: analytics,
      totalRevenue: analytics.total_amount,
      totalTransactions,
      successRate,
      conversionRate: successRate,
      paymentMethods: analytics.payment_methods,
      recentCharges: chargesResponse.data || [],
      source: 'pagarme',
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar analytics do Pagar.me:', error)

    const analytics = emptyAnalytics(startDate, endDate)
    return NextResponse.json({
      success: true,
      data: analytics,
      totalRevenue: analytics.total_amount,
      totalTransactions: analytics.total_orders,
      successRate: 0,
      conversionRate: 0,
      paymentMethods: analytics.payment_methods,
      recentCharges: [],
      source: 'empty',
      warning: 'Erro ao buscar analytics do Pagar.me',
    })
  }
}
