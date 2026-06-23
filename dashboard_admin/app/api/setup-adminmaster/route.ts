import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import bcrypt from 'bcryptjs'
import { FULL_ADMIN_PERMISSIONS } from '@/lib/admin-permissions'

const BCRYPT_ROUNDS = 10

export async function POST(request: NextRequest) {
  try {
    console.log('🔥 Inicializando setup do AdminMaster...')

    let db: FirebaseFirestore.Firestore
    try {
      db = getAdminFirestore()
    } catch {
      return NextResponse.json(
        { success: false, error: 'Firebase Admin SDK não inicializado. Configure FIREBASE_SERVICE_ACCOUNT.' },
        { status: 503 }
      )
    }

    const body = await request.json().catch(() => ({}))
    const email = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : ''
    const senha = typeof body?.password === 'string' ? body.password : ''
    const nome = typeof body?.nome === 'string' ? body.nome.trim() : ''

    const existing = await db.collection('adminmaster').doc('master').get()
    if (existing.exists) {
      return NextResponse.json(
        { success: false, error: 'AdminMaster já configurado; o setup não pode sobrescrevê-lo' },
        { status: 409 }
      )
    }

    const configuredToken = process.env.ADMIN_SETUP_TOKEN?.trim()
    const suppliedToken = request.headers.get('x-admin-setup-token')?.trim()
    if (process.env.NODE_ENV === 'production' && (!configuredToken || suppliedToken !== configuredToken)) {
      return NextResponse.json(
        { success: false, error: 'Token de configuração inicial inválido' },
        { status: 403 }
      )
    }

    if (!email || !nome || senha.length < 10) {
      return NextResponse.json(
        { success: false, error: 'Informe nome, email e uma senha com pelo menos 10 caracteres' },
        { status: 400 }
      )
    }

    console.log('👑 Configurando AdminMaster...')

    const senhaHash = await bcrypt.hash(senha, BCRYPT_ROUNDS)

    const adminMasterData = {
      email,
      senhaHash,
      nome,
      permissoes: FULL_ADMIN_PERMISSIONS,
      criadoEm: new Date().toISOString(),
      ativo: true,
    }

    await db.collection('adminmaster').doc('master').set(adminMasterData)
    console.log('✅ AdminMaster configurado com sucesso!')

    await db
      .collection('adminmaster')
      .doc('master')
      .collection('configuracoes')
      .doc('sistema')
      .set({
        versao: '1.0.0',
        ultimaAtualizacao: new Date().toISOString(),
        configuracoes: {
          maxUsuarios: 100,
          sessaoTimeout: 3600,
          logAtividades: true,
          notificacoes: true,
        },
      })

    await db
      .collection('adminmaster')
      .doc('master')
      .collection('logs')
      .add({
        tipo: 'sistema',
        acao: 'setup_inicial',
        descricao: 'Sistema AdminMaster configurado com sucesso',
        timestamp: new Date().toISOString(),
        usuario: 'sistema',
      })

    return NextResponse.json({
      success: true,
      message: 'AdminMaster configurado com sucesso!',
      data: {
        adminMaster: { email, nome },
      },
    })
  } catch (error) {
    console.error('❌ Erro ao configurar AdminMaster:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro interno do servidor',
        details: error instanceof Error ? error.message : 'Erro desconhecido',
      },
      { status: 500 }
    )
  }
}

export async function GET() {
  return NextResponse.json({
    message: 'Configuração inicial protegida',
  })
}
