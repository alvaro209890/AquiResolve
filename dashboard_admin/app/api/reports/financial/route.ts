import { NextRequest, NextResponse } from 'next/server'
import { getCollection } from '@/lib/firestore'
import { toDateFromUnknown } from '@/lib/date-utils'
import { adminAuthorizationResponse, requireAnyAdminPermission } from '@/lib/server/admin-authorization'

type TransactionRecord = Record<string, unknown> & {
  id: string
  tipo?: string
  categoria?: string
  descricao?: string
  valor?: number
  data?: string
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

const buildCategories = (transactions: TransactionRecord[]) => {
  const totals = transactions.reduce<Record<string, number>>((accumulator, transaction) => {
    const category = readString(transaction.categoria) || 'sem_categoria'
    accumulator[category] = (accumulator[category] || 0) + readNumber(transaction.valor)
    return accumulator
  }, {})

  const total = Object.values(totals).reduce((sum, value) => sum + value, 0)

  return Object.entries(totals)
    .sort(([, a], [, b]) => b - a)
    .map(([nome, valor]) => ({
      nome,
      valor,
      percentual: total > 0 ? Number(((valor / total) * 100).toFixed(2)) : 0,
    }))
}

export async function GET(request: NextRequest) {
  try {
    await requireAnyAdminPermission(request, ['financeiro', 'relatorios'])
    const { searchParams } = new URL(request.url)
    const periodo = searchParams.get('periodo') || 'mes'
    const tipo = searchParams.get('tipo') || 'resumo'
    const now = new Date()

    let startDate = new Date(now.getFullYear(), now.getMonth(), 1)
    if (periodo === 'semana') {
      startDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000)
    } else if (periodo === 'ano') {
      startDate = new Date(now.getFullYear(), 0, 1)
    }

    const transactions = ((await getCollection('transactions')) as TransactionRecord[]).filter((transaction) => {
      const transactionDate = toDateFromUnknown(transaction.data ?? transaction.createdAt, new Date(0))
      return transactionDate >= startDate && transactionDate <= now
    })

    const receitas = transactions.filter((transaction) => readString(transaction.tipo) === 'receita')
    const despesas = transactions.filter((transaction) => readString(transaction.tipo) === 'despesa')

    const totalReceitas = receitas.reduce((sum, transaction) => sum + readNumber(transaction.valor), 0)
    const totalDespesas = despesas.reduce((sum, transaction) => sum + readNumber(transaction.valor), 0)
    const saldo = totalReceitas - totalDespesas

    const resumo = {
      receitas: {
        total: totalReceitas,
        crescimento: null,
        categorias: buildCategories(receitas),
      },
      despesas: {
        total: totalDespesas,
        crescimento: null,
        categorias: buildCategories(despesas),
      },
      saldo,
      margem: totalReceitas > 0 ? Number(((saldo / totalReceitas) * 100).toFixed(2)) : 0,
    }

    const detalhado = {
      receitas: receitas.map((transaction) => ({
        data: toDateFromUnknown(transaction.data ?? transaction.createdAt, new Date(0))
          .toISOString()
          .split('T')[0],
        valor: readNumber(transaction.valor),
        descricao: readString(transaction.descricao),
      })),
      despesas: despesas.map((transaction) => ({
        data: toDateFromUnknown(transaction.data ?? transaction.createdAt, new Date(0))
          .toISOString()
          .split('T')[0],
        valor: readNumber(transaction.valor),
        descricao: readString(transaction.descricao),
      })),
    }

    return NextResponse.json({
      success: true,
      data: tipo === 'detalhado' ? detalhado : resumo,
      periodo,
      tipo,
      geradoEm: new Date().toISOString(),
      warning:
        transactions.length === 0
          ? 'Nenhuma transacao real encontrada para gerar o relatorio financeiro.'
          : undefined,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao gerar relatorio financeiro:', error)
    return NextResponse.json(
      { success: false, error: 'Erro interno do servidor' },
      { status: 500 }
    )
  }
}
