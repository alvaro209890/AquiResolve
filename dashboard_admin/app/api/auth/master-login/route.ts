import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import bcrypt from 'bcryptjs'
import {
  MASTER_SESSION_COOKIE,
  createMasterSessionToken,
  masterSessionCookieOptions,
} from '@/lib/server/master-session'
import { normalizeAdminPermissions } from '@/lib/admin-permissions'

const BCRYPT_PREFIX = '$2'

function comparePassword(password: string, senhaHash: string): boolean {
  if (!senhaHash || typeof senhaHash !== 'string') return false
  if (senhaHash.startsWith(BCRYPT_PREFIX)) {
    return bcrypt.compareSync(password, senhaHash)
  }
  const base64 = Buffer.from(password, 'utf8').toString('base64')
  return base64 === senhaHash
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json()
    const email = typeof body?.email === 'string' ? body.email.trim() : ''
    const password = typeof body?.password === 'string' ? body.password : ''

    if (!email || !password) {
      return NextResponse.json(
        { error: 'Email e senha são obrigatórios' },
        { status: 400 }
      )
    }

    const db = getAdminFirestore()
    const docRef = db.collection('adminmaster').doc('master')
    const snap = await docRef.get()

    if (!snap.exists) {
      return NextResponse.json(
        { error: 'MASTER_NOT_FOUND' },
        { status: 401 }
      )
    }

    const data = snap.data() as {
      email?: string
      senhaHash?: string
      nome?: string
      permissoes?: Record<string, boolean>
    }

    if (data.email !== email) {
      return NextResponse.json(
        { error: 'Credenciais inválidas' },
        { status: 401 }
      )
    }

    const senhaHash = data.senhaHash
    if (!senhaHash || typeof senhaHash !== 'string') {
      return NextResponse.json(
        { error: 'MASTER_INVALID_CONFIG' },
        { status: 401 }
      )
    }

    if (!comparePassword(password, senhaHash)) {
      return NextResponse.json(
        { error: 'Credenciais inválidas' },
        { status: 401 }
      )
    }

    const response = NextResponse.json({
      user: {
        id: snap.id,
        email: data.email,
        nome: data.nome ?? 'Administrador Master',
        permissoes: normalizeAdminPermissions(data.permissoes, { master: true }),
      },
    })
    response.cookies.set(
      MASTER_SESSION_COOKIE,
      createMasterSessionToken(senhaHash),
      masterSessionCookieOptions
    )
    return response
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : String(err)
    if (message.includes('Firebase Admin não inicializado')) {
      return NextResponse.json(
        { error: 'Serviço indisponível. Configure FIREBASE_SERVICE_ACCOUNT.' },
        { status: 503 }
      )
    }
    console.error('Erro no login master (API):', err)
    return NextResponse.json(
      { error: 'Erro interno' },
      { status: 500 }
    )
  }
}
