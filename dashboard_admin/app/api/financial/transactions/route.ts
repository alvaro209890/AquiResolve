import { NextRequest, NextResponse } from 'next/server'
import { addDocument, getCollection } from '@/lib/firestore'
import { toDateFromUnknown } from '@/lib/date-utils'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

type TransactionRecord = Record<string, unknown> & {
  id: string
  tipo?: string
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

const getTransactionDate = (transaction: TransactionRecord) =>
  toDateFromUnknown(transaction.data ?? transaction.createdAt, new Date(0))

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const { searchParams } = new URL(request.url)
    const tipo = searchParams.get('tipo')
    const status = searchParams.get('status')
    const conta = searchParams.get('conta')
    const dataInicio = searchParams.get('dataInicio')
    const dataFim = searchParams.get('dataFim')
    const page = Math.max(parseInt(searchParams.get('page') || '1', 10), 1)
    const limit = Math.max(parseInt(searchParams.get('limit') || '10', 10), 1)

    let transactions = (await getCollection('transactions')) as TransactionRecord[]

    transactions = transactions.sort((a, b) => getTransactionDate(b).getTime() - getTransactionDate(a).getTime())

    if (tipo) {
      transactions = transactions.filter((transaction) => readString(transaction.tipo) === tipo)
    }

    if (status) {
      transactions = transactions.filter((transaction) => readString(transaction.status) === status)
    }

    if (conta) {
      transactions = transactions.filter((transaction) => {
        const contaNome =
          readString((transaction.conta as Record<string, unknown> | undefined)?.nome) ||
          readString(transaction.conta)
        return contaNome === conta
      })
    }

    if (dataInicio) {
      const start = new Date(`${dataInicio}T00:00:00`)
      transactions = transactions.filter((transaction) => getTransactionDate(transaction) >= start)
    }

    if (dataFim) {
      const end = new Date(`${dataFim}T23:59:59.999`)
      transactions = transactions.filter((transaction) => getTransactionDate(transaction) <= end)
    }

    const startIndex = (page - 1) * limit
    const paginatedTransactions = transactions.slice(startIndex, startIndex + limit)
    const totalReceitas = transactions
      .filter((transaction) => readString(transaction.tipo) === 'receita')
      .reduce((sum, transaction) => sum + readNumber(transaction.valor), 0)
    const totalDespesas = transactions
      .filter((transaction) => readString(transaction.tipo) === 'despesa')
      .reduce((sum, transaction) => sum + readNumber(transaction.valor), 0)

    return NextResponse.json({
      success: true,
      data: paginatedTransactions,
      pagination: {
        page,
        limit,
        total: transactions.length,
        totalPages: Math.ceil(transactions.length / limit),
      },
      summary: {
        totalReceitas,
        totalDespesas,
        saldo: totalReceitas - totalDespesas,
      },
      warning:
        transactions.length === 0
          ? 'Nenhuma transacao real encontrada na colecao transactions.'
          : undefined,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao listar transacoes:', error)
    return NextResponse.json(
      { success: false, error: 'Erro interno do servidor' },
      { status: 500 }
    )
  }
}

export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()

    if (!body.descricao || !body.tipo || body.valor === undefined || !body.conta) {
      return NextResponse.json(
        { success: false, error: 'Dados obrigatorios nao fornecidos' },
        { status: 400 }
      )
    }

    const transactionId = await addDocument('transactions', {
      ...body,
      data: body.data || new Date().toISOString().split('T')[0],
      status: body.status || 'pendente',
    })

    return NextResponse.json(
      {
        success: true,
        data: {
          id: transactionId,
          ...body,
          data: body.data || new Date().toISOString().split('T')[0],
          status: body.status || 'pendente',
        },
        message: 'Transacao criada com sucesso',
      },
      { status: 201 }
    )
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao criar transacao:', error)
    return NextResponse.json(
      { success: false, error: 'Erro interno do servidor' },
      { status: 500 }
    )
  }
}
