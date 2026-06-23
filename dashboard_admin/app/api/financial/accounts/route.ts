import { NextRequest, NextResponse } from 'next/server'
import { addDocument, getCollection } from '@/lib/firestore'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

type AccountRecord = Record<string, unknown> & {
  id: string
  tipo?: string
  status?: string
}

const readString = (value: unknown): string => (typeof value === 'string' ? value.trim() : '')

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const { searchParams } = new URL(request.url)
    const tipo = searchParams.get('tipo')
    const status = searchParams.get('status')

    let accounts = (await getCollection('accounts')) as AccountRecord[]

    if (tipo) {
      accounts = accounts.filter((account) => readString(account.tipo) === tipo)
    }

    if (status) {
      accounts = accounts.filter((account) => readString(account.status) === status)
    }

    return NextResponse.json({
      success: true,
      data: accounts,
      total: accounts.length,
      warning:
        accounts.length === 0
          ? 'Nenhuma conta real encontrada na colecao accounts.'
          : undefined,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar contas:', error)
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

    if (!body.nome || !body.banco || !body.agencia || !body.conta) {
      return NextResponse.json(
        { success: false, error: 'Dados obrigatorios nao fornecidos' },
        { status: 400 }
      )
    }

    const accountId = await addDocument('accounts', {
      ...body,
      saldo: typeof body.saldo === 'number' ? body.saldo : 0,
      status: body.status || 'ativa',
    })

    return NextResponse.json(
      {
        success: true,
        data: {
          id: accountId,
          ...body,
          saldo: typeof body.saldo === 'number' ? body.saldo : 0,
          status: body.status || 'ativa',
        },
        message: 'Conta criada com sucesso',
      },
      { status: 201 }
    )
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao criar conta:', error)
    return NextResponse.json(
      { success: false, error: 'Erro interno do servidor' },
      { status: 500 }
    )
  }
}
