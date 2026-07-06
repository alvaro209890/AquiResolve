import type { NextRequest } from 'next/server'
import { z } from 'zod'

const CONFIRMATION = 'EXCLUIR MINHA CONTA' as const

/** UID Firebase: 1–128 chars, sem espaços nem caracteres de controle */
export const deleteAccountBodySchema = z.object({
  userId: z
    .string()
    .min(1, 'userId inválido')
    .max(128)
    .refine((s) => !/[\s\r\n\0\u0001-\u001F]/.test(s), { message: 'userId inválido' }),
  confirmationText: z
    .string()
    .refine((s) => s === CONFIRMATION, { message: 'Texto de confirmação inválido' }),
})

export type DeleteAccountBody = z.infer<typeof deleteAccountBodySchema>

const MAX_BODY_BYTES = 8 * 1024

export function getClientIp(req: NextRequest): string {
  const forwarded = req.headers.get('x-forwarded-for')
  if (forwarded) {
    return forwarded.split(',')[0]?.trim() || 'unknown'
  }
  const real = req.headers.get('x-real-ip')
  if (real) return real.trim()
  return req.headers.get('cf-connecting-ip')?.trim() || 'unknown'
}

/**
 * CORS: em produção, defina ALLOWED_ORIGINS (origens separadas por vírgula).
 * Sem isso em produção, só requisições same-origin (sem header Origin) ou sem CORS aberto.
 */
export function corsHeadersFor(req: NextRequest): Record<string, string> {
  const origin = req.headers.get('origin')
  const raw = process.env.ALLOWED_ORIGINS?.trim()
  const allowed = raw
    ? raw.split(',').map((o) => o.trim().replace(/\/$/, '')).filter(Boolean)
    : []

  const base: Record<string, string> = {
    'Access-Control-Allow-Methods': 'POST, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Max-Age': '86400',
  }

  if (allowed.length === 0) {
    if (process.env.NODE_ENV !== 'production') {
      return { ...base, 'Access-Control-Allow-Origin': origin || '*' }
    }
    if (origin) {
      return base
    }
    return base
  }

  if (origin && allowed.includes(origin)) {
    return { ...base, 'Access-Control-Allow-Origin': origin, Vary: 'Origin' }
  }

  if (!origin) {
    return base
  }

  return base
}

export function mergeSecurityHeaders(
  cors: Record<string, string>,
): Record<string, string> {
  return {
    ...cors,
    'Cache-Control': 'no-store, no-cache, must-revalidate, private',
    Pragma: 'no-cache',
    'X-Content-Type-Options': 'nosniff',
  }
}

export async function readJsonBodyLimited(req: NextRequest): Promise<
  | { ok: true; data: unknown }
  | { ok: false; status: 413 | 400; message: string }
> {
  const len = req.headers.get('content-length')
  if (len && Number(len) > MAX_BODY_BYTES) {
    return { ok: false, status: 413, message: 'Corpo da requisição muito grande' }
  }

  const text = await req.text()
  if (text.length > MAX_BODY_BYTES) {
    return { ok: false, status: 413, message: 'Corpo da requisição muito grande' }
  }

  const ct = req.headers.get('content-type')?.toLowerCase() ?? ''
  if (!ct.includes('application/json')) {
    return { ok: false, status: 400, message: 'Content-Type deve ser application/json' }
  }

  try {
    return { ok: true, data: JSON.parse(text) as unknown }
  } catch {
    return { ok: false, status: 400, message: 'JSON inválido' }
  }
}

type Bucket = { count: number; resetAt: number }

const WINDOW_MS = 60_000
const MAX_PER_WINDOW = 8
const buckets = new Map<string, Bucket>()
const BUCKET_CAP = 50_000

function pruneBuckets(now: number): void {
  if (buckets.size <= BUCKET_CAP) return
  for (const [k, v] of buckets) {
    if (now > v.resetAt) buckets.delete(k)
  }
}

export function rateLimitAccountDelete(ip: string): { allowed: true } | { allowed: false; retryAfterSec: number } {
  const now = Date.now()
  pruneBuckets(now)

  let b = buckets.get(ip)
  if (!b || now > b.resetAt) {
    b = { count: 0, resetAt: now + WINDOW_MS }
    buckets.set(ip, b)
  }

  b.count += 1
  if (b.count > MAX_PER_WINDOW) {
    return { allowed: false, retryAfterSec: Math.max(1, Math.ceil((b.resetAt - now) / 1000)) }
  }
  return { allowed: true }
}

export function isProduction(): boolean {
  return process.env.NODE_ENV === 'production'
}

export function safePublicError(_err: unknown): string {
  return isProduction() ? 'Erro interno do servidor' : (_err instanceof Error ? _err.message : 'Erro interno')
}
