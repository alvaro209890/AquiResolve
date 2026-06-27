import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import { PagarmeService } from '@/lib/services/pagarme-service'

export const dynamic = 'force-dynamic'

interface DailyRevenue {
  date: string
  receita: number
  transacoes: number
}

interface PaymentMethodTrend {
  date: string
  credit_card: number
  debit_card: number
  pix: number
  boleto: number
}

interface AnalyticsResponse {
  success: boolean
  data?: {
    // KPIs
    totalRevenue: number
    totalTransactions: number
    averageTicket: number
    successRate: number
    conversionRate: number
    totalCustomers: number
    totalProviders: number
    platformCommission: number
    cashbackDistributed: number
    pendingProviderPayouts: number

    // Charts
    dailyRevenue: DailyRevenue[]
    paymentMethodTrend: PaymentMethodTrend[]
    paymentMethods: {
      credit_card: number
      debit_card: number
      pix: number
      boleto: number
    }
    statusBreakdown: {
      paid: number
      pending: number
      failed: number
      canceled: number
    }

    // Top lists
    topServices: { name: string; revenue: number; count: number }[]
    recentTransactions: any[]

    period: { start: string; end: string }
    source: string
  }
  warning?: string
  error?: string
}

function getDateRange(period: string) {
  const now = new Date()
  const end = now.toISOString().split('T')[0]

  let start: Date
  switch (period) {
    case '7d':
      start = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
      break
    case '30d':
      start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
      break
    case '90d':
      start = new Date(now.getTime() - 90 * 24 * 60 * 60 * 1000)
      break
    case 'mes':
      start = new Date(now.getFullYear(), now.getMonth(), 1)
      break
    case 'ano':
      start = new Date(now.getFullYear(), 0, 1)
      break
    default:
      start = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000)
  }

  return {
    startDate: start.toISOString().split('T')[0],
    endDate: end,
  }
}

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')

    const { searchParams } = new URL(request.url)
    const period = searchParams.get('period') || '30d'
    const { startDate, endDate } = getDateRange(period)

    const db = getAdminFirestore()

    // Buscar dados do Firestore em paralelo
    const [ordersSnapshot, providersSnapshot, settlementsSnapshot, usersSnapshot] =
      await Promise.all([
        db.collection('orders').get(),
        db.collection('providers').get(),
        db.collection('order_settlements').get(),
        db.collection('users').get(),
      ])

    // Processar pedidos
    interface OrderData {
      id: string
      status: string
      totalAmount: number
      paymentMethod: string
      createdAt: Date | null
      serviceName: string
      providerCommission: number
      providerId: string
    }

    const orders: OrderData[] = []
    ordersSnapshot.forEach((doc) => {
      const data = doc.data() || {}
      const createdAt =
        data.createdAt?.toDate?.() ||
        (data.createdAt instanceof Date ? data.createdAt : null)

      const orderDate = createdAt
        ? createdAt.toISOString().split('T')[0]
        : null

      if (!orderDate || orderDate < startDate || orderDate > endDate) return

      orders.push({
        id: doc.id,
        status: String(data.status || ''),
        totalAmount: Number(data.totalAmount) || 0,
        paymentMethod: String(data.paymentMethod || ''),
        createdAt,
        serviceName: String(data.serviceName || data.service || ''),
        providerCommission: Number(data.providerCommission) || 0,
        providerId: String(data.assignedProvider || data.providerId || ''),
      })
    })

    // Calcular KPIs
    const totalRevenue = orders
      .filter((o) => o.status === 'completed')
      .reduce((sum, o) => sum + o.totalAmount, 0)

    const totalTransactions = orders.length
    const paidOrders = orders.filter((o) => o.status === 'completed')
    const successRate =
      totalTransactions > 0
        ? Math.round((paidOrders.length / totalTransactions) * 1000) / 10
        : 0

    const averageTicket =
      paidOrders.length > 0
        ? Math.round(totalRevenue / paidOrders.length)
        : 0

    const conversionRate = successRate // placeholder - Pagar.me fornece melhor

    // Métodos de pagamento
    const paymentMethods = { credit_card: 0, debit_card: 0, pix: 0, boleto: 0 }
    orders.forEach((o) => {
      const method = o.paymentMethod?.toLowerCase() || ''
      if (method.includes('credit')) paymentMethods.credit_card++
      else if (method.includes('debit')) paymentMethods.debit_card++
      else if (method.includes('pix')) paymentMethods.pix++
      else if (method.includes('boleto')) paymentMethods.boleto++
    })

    // Status breakdown
    const statusBreakdown = { paid: 0, pending: 0, failed: 0, canceled: 0 }
    orders.forEach((o) => {
      const s = o.status?.toLowerCase() || ''
      if (s === 'completed' || s === 'paid') statusBreakdown.paid++
      else if (s === 'pending' || s === 'in_progress' || s === 'assigned')
        statusBreakdown.pending++
      else if (s === 'failed') statusBreakdown.failed++
      else if (s === 'cancelled' || s === 'canceled') statusBreakdown.canceled++
    })

    // Totais
    const totalCustomers = usersSnapshot.docs.filter((doc) => {
      const data = doc.data() || {}
      return String(data.role || '').toLowerCase() === 'client' || String(data.role || '').toLowerCase() === 'cliente'
    }).length

    const totalProviders = providersSnapshot.size

    // Settlements
    let platformCommission = 0
    let cashbackDistributed = 0
    settlementsSnapshot.forEach((doc) => {
      const data = doc.data() || {}
      platformCommission += Number(data.platformCommission) || 0
      cashbackDistributed += Number(data.cashbackAmount) || 0
    })

    // Pending provider payouts
    let pendingProviderPayouts = 0
    // Usar orders que tem providerCommission > 0 e estão completed
    const providerEarnings = new Map<string, number>()
    orders
      .filter((o) => o.status === 'completed' && o.providerCommission > 0)
      .forEach((o) => {
        if (o.providerId) {
          providerEarnings.set(
            o.providerId,
            (providerEarnings.get(o.providerId) || 0) + o.providerCommission
          )
        }
      })
    pendingProviderPayouts = Array.from(providerEarnings.values()).reduce(
      (sum, v) => sum + v,
      0
    )

    // Receita diária
    const dailyMap = new Map<string, { receita: number; transacoes: number }>()
    const startDateObj = new Date(
      new Date(startDate + 'T00:00:00').getTime() + // normalize
      new Date().getTimezoneOffset() * 60000
    )
    const endDateObj = new Date(endDate + 'T23:59:59')

    for (
      let d = new Date(startDateObj);
      d <= endDateObj;
      d.setDate(d.getDate() + 1)
    ) {
      const key = d.toISOString().split('T')[0]
      dailyMap.set(key, { receita: 0, transacoes: 0 })
    }

    orders
      .filter((o) => o.status === 'completed')
      .forEach((o) => {
        if (!o.createdAt) return
        const key = o.createdAt.toISOString().split('T')[0]
        const entry = dailyMap.get(key)
        if (entry) {
          entry.receita += o.totalAmount
          entry.transacoes++
        }
      })

    const dailyRevenue: DailyRevenue[] = Array.from(dailyMap.entries())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([date, vals]) => ({
        date,
        receita: vals.receita,
        transacoes: vals.transacoes,
      }))

    // Top services
    const serviceRevenue = new Map<string, { revenue: number; count: number }>()
    orders
      .filter((o) => o.status === 'completed')
      .forEach((o) => {
        const name = o.serviceName || 'Sem categoria'
        const entry = serviceRevenue.get(name) || { revenue: 0, count: 0 }
        entry.revenue += o.totalAmount
        entry.count++
        serviceRevenue.set(name, entry)
      })

    const topServices = Array.from(serviceRevenue.entries())
      .sort(([, a], [, b]) => b.revenue - a.revenue)
      .slice(0, 10)
      .map(([name, val]) => ({ name, ...val }))

    // Recent transactions
    const recentTransactions = orders
      .sort(
        (a, b) =>
          (b.createdAt?.getTime() || 0) - (a.createdAt?.getTime() || 0)
      )
      .slice(0, 10)
      .map((o) => ({
        id: o.id.slice(-8),
        status: o.status,
        amount: o.totalAmount,
        method: o.paymentMethod,
        date: o.createdAt?.toISOString() || '',
        service: o.serviceName,
      }))

    return NextResponse.json({
      success: true,
      data: {
        totalRevenue,
        totalTransactions,
        averageTicket,
        successRate,
        conversionRate,
        totalCustomers,
        totalProviders,
        platformCommission,
        cashbackDistributed,
        pendingProviderPayouts,
        dailyRevenue,
        paymentMethodTrend: [],
        paymentMethods,
        statusBreakdown,
        topServices,
        recentTransactions,
        period: { start: startDate, end: endDate },
        source: 'firebase',
      },
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar analytics financeiro:', error)
    return NextResponse.json(
      { success: false, error: 'Erro ao buscar dados financeiros' },
      { status: 500 }
    )
  }
}
