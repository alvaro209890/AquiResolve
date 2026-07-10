import { NextRequest, NextResponse } from 'next/server'
import { getAdminAuth, getAdminFirestore } from '@/lib/firebase-admin'
import { AccountDeletionService } from '@/lib/services/account-deletion-service'
import {
  corsHeadersFor,
  deleteAccountBodySchema,
  getClientIp,
  isProduction,
  mergeSecurityHeaders,
  rateLimitAccountDelete,
  readJsonBodyLimited,
  safePublicError,
} from '@/lib/security/account-delete-api'

export const maxDuration = 300

function json(
  req: NextRequest,
  body: Record<string, unknown>,
  status: number,
): NextResponse {
  const h = mergeSecurityHeaders(corsHeadersFor(req))
  return NextResponse.json(body, { status, headers: h })
}

export async function OPTIONS(req: NextRequest) {
  const h = mergeSecurityHeaders(corsHeadersFor(req))
  return new NextResponse(null, { status: 204, headers: h })
}

export async function POST(req: NextRequest) {
  const cors = corsHeadersFor(req)
  const headers = mergeSecurityHeaders(cors)

  try {
    const origin = req.headers.get('origin')
    if (isProduction() && process.env.ALLOWED_ORIGINS?.trim()) {
      const allowed = process.env.ALLOWED_ORIGINS.split(',').map((o) => o.trim().replace(/\/$/, ''))
      if (origin && !allowed.includes(origin)) {
        return NextResponse.json(
          { success: false, error: 'Origem não permitida' },
          { status: 403, headers },
        )
      }
    }

    const ip = getClientIp(req)
    const limited = rateLimitAccountDelete(ip)
    if (!limited.allowed) {
      return NextResponse.json(
        { success: false, error: 'Muitas tentativas. Aguarde e tente novamente.' },
        {
          status: 429,
          headers: { ...headers, 'Retry-After': String(limited.retryAfterSec) },
        },
      )
    }

    const authHeader = req.headers.get('Authorization')
    if (!authHeader?.startsWith('Bearer ')) {
      return json(req, { success: false, error: 'Token de autenticação não fornecido' }, 401)
    }

    const idToken = authHeader.slice(7).trim()
    if (!idToken || idToken.length > 16_384) {
      return json(req, { success: false, error: 'Token inválido' }, 401)
    }

    const rawBody = await readJsonBodyLimited(req)
    if (!rawBody.ok) {
      return json(req, { success: false, error: rawBody.message }, rawBody.status)
    }

    const parsed = deleteAccountBodySchema.safeParse(rawBody.data)
    if (!parsed.success) {
      const msg = parsed.error.issues[0]?.message ?? 'Dados inválidos'
      return json(req, { success: false, error: msg }, 400)
    }

    const { userId } = parsed.data

    let auth: ReturnType<typeof getAdminAuth>
    let db: ReturnType<typeof getAdminFirestore>

    try {
      auth = getAdminAuth()
      db = getAdminFirestore()
    } catch {
      return json(req, { success: false, error: 'Serviço indisponível' }, 503)
    }

    let decodedToken: Awaited<ReturnType<typeof auth.verifyIdToken>>
    try {
      decodedToken = await auth.verifyIdToken(idToken, true)
    } catch {
      return json(req, { success: false, error: 'Token inválido ou expirado' }, 401)
    }

    if (decodedToken.uid !== userId) {
      return json(req, { success: false, error: 'Não autorizado' }, 403)
    }

    const userDoc = await db.collection('users').doc(userId).get()
    const userData = userDoc.data() as { role?: string; email?: string } | undefined
    const role = userData?.role

    if (role === 'admin' || role === 'operador') {
      return json(
        req,
        {
          success: false,
          error: 'Administradores e operadores não podem se auto-excluir por este endpoint',
        },
        403,
      )
    }

    const userEmail = userData?.email ?? decodedToken.email ?? ''
    const uidLog = userId.length > 8 ? `${userId.slice(0, 8)}…` : userId
    console.log(`[account/delete] start uid=${uidLog} role=${role ?? 'n/a'}`)

    const service = new AccountDeletionService()
    const result = await service.deleteAccount(userId, userEmail)

    if (!result.phases.authDelete.success) {
      return NextResponse.json(
        {
          success: false,
          error: 'Falha ao excluir usuário do Firebase Authentication. Dados foram parcialmente removidos.',
          phases: result.phases,
          deletedResources: result.deletedResources,
        },
        { status: 500, headers },
      )
    }

    return NextResponse.json(
      {
        success: true,
        message: 'Conta excluída com sucesso. Todos os dados foram removidos conforme LGPD.',
        phases: result.phases,
        deletedResources: result.deletedResources,
      },
      { headers },
    )
  } catch (error: unknown) {
    console.error('[account/delete] error:', error instanceof Error ? error.message : 'unknown')
    return json(req, { success: false, error: safePublicError(error) }, 500)
  }
}
