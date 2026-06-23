import { NextRequest, NextResponse } from 'next/server'
import { getCollection } from '@/lib/firestore'
import { toDateFromUnknown, toIsoStringFromUnknown } from '@/lib/date-utils'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

type OrderRecord = Record<string, unknown> & {
  id: string
  status?: string
}

const readString = (value: unknown): string => (typeof value === 'string' ? value.trim() : '')

const readNumber = (value: unknown): number => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }

  if (typeof value === 'string') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : 0
  }

  return 0
}

const getOrderDate = (order: OrderRecord): Date =>
  toDateFromUnknown(order.createdAt ?? order.created_at ?? order.updatedAt, new Date(0))

const getOrderAmount = (order: OrderRecord): number =>
  readNumber(
    order.amount ??
      order.totalAmount ??
      order.total ??
      order.valor ??
      order.price ??
      order.servicePrice
  )

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gestaoPedidos')
    const { searchParams } = new URL(request.url)
    const status = searchParams.get('status')
    const cliente = searchParams.get('cliente')?.trim().toLowerCase()
    const dataInicio = searchParams.get('dataInicio')
    const dataFim = searchParams.get('dataFim')
    const page = Math.max(parseInt(searchParams.get('page') || '1', 10), 1)
    const limit = Math.max(parseInt(searchParams.get('limit') || '10', 10), 1)

    let orders = (await getCollection('orders')) as OrderRecord[]

    orders = orders.sort((a, b) => getOrderDate(b).getTime() - getOrderDate(a).getTime())

    if (status) {
      orders = orders.filter((order) => readString(order.status) === status)
    }

    if (cliente) {
      orders = orders.filter((order) => {
        const values = [
          order.clientName,
          order.customerName,
          order.clientEmail,
          order.customerEmail,
          order.protocol,
          order.serviceName,
          order.serviceType,
          order.address,
        ]

        return values.some((value) => readString(value).toLowerCase().includes(cliente))
      })
    }

    if (dataInicio) {
      const start = new Date(`${dataInicio}T00:00:00`)
      orders = orders.filter((order) => getOrderDate(order) >= start)
    }

    if (dataFim) {
      const end = new Date(`${dataFim}T23:59:59.999`)
      orders = orders.filter((order) => getOrderDate(order) <= end)
    }

    const startIndex = (page - 1) * limit
    const paginatedOrders = orders.slice(startIndex, startIndex + limit)
    const totalValor = orders.reduce((sum, order) => sum + getOrderAmount(order), 0)
    const statusCounts = orders.reduce<Record<string, number>>((accumulator, order) => {
      const currentStatus = readString(order.status) || 'sem_status'
      accumulator[currentStatus] = (accumulator[currentStatus] || 0) + 1
      return accumulator
    }, {})

    return NextResponse.json({
      success: true,
      data: paginatedOrders,
      pagination: {
        page,
        limit,
        total: orders.length,
        totalPages: Math.ceil(orders.length / limit),
      },
      summary: {
        totalValor,
        statusCounts,
      },
      warning:
        orders.length === 0
          ? 'Nenhum pedido real encontrado na colecao orders para os filtros informados.'
          : undefined,
      generatedAt: toIsoStringFromUnknown(new Date()),
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao listar pedidos:', error)
    return NextResponse.json(
      { success: false, error: 'Erro interno do servidor' },
      { status: 500 }
    )
  }
}

export async function POST() {
  return NextResponse.json(
    {
      success: false,
      error: 'Criacao de pedidos por esta rota nao esta habilitada sem um contrato real de persistencia.',
    },
    { status: 501 }
  )
}
