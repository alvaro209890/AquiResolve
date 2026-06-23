import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'

// GET /api/provider-chats?status=active|archived&unreadOnly=true&limit=100
// Lista os chats Base↔Prestador (coleção `provider_chats`), ordenados por lastMessageAt desc.
//
// Espelha /api/client-chats. Filtra e ordena EM MEMÓRIA (padrão dos banners/combos) para não
// exigir índice composto no Firestore e ser robusto a docs antigos sem o campo `archived`.

/** Extrai milissegundos de um campo de data (Timestamp, número, string ISO ou ausente). */
function toMillis(value: unknown): number {
  if (!value) return 0
  if (typeof value === 'number') return value
  if (typeof value === 'string') {
    const t = Date.parse(value)
    return Number.isNaN(t) ? 0 : t
  }
  if (typeof value === 'object') {
    const v = value as { toMillis?: () => number; _seconds?: number; seconds?: number }
    if (typeof v.toMillis === 'function') return v.toMillis()
    if (typeof v._seconds === 'number') return v._seconds * 1000
    if (typeof v.seconds === 'number') return v.seconds * 1000
  }
  return 0
}

export async function GET(request: NextRequest) {
  try {
    const db = getAdminFirestore()
    const { searchParams } = new URL(request.url)
    const status = searchParams.get('status') ?? 'active'
    const unreadOnly = searchParams.get('unreadOnly') === 'true'
    const limit = Math.min(parseInt(searchParams.get('limit') ?? '100', 10) || 100, 500)

    const snap = await db.collection('provider_chats').get()

    let chats = snap.docs.map((d) => {
      const data = d.data() as Record<string, unknown>
      return {
        id: d.id,
        ...data,
        archived: Boolean(data.archived),
        unreadByAdmin: Number(data.unreadByAdmin ?? 0),
        _lastMessageMillis: toMillis(data.lastMessageAt),
      }
    })

    if (status === 'archived') {
      chats = chats.filter((c) => c.archived)
    } else if (status === 'active') {
      chats = chats.filter((c) => !c.archived)
    }

    if (unreadOnly) {
      chats = chats.filter((c) => c.unreadByAdmin > 0)
    }

    chats.sort((a, b) => b._lastMessageMillis - a._lastMessageMillis)

    const result = chats.slice(0, limit).map((c) => {
      const { _lastMessageMillis, ...rest } = c
      void _lastMessageMillis
      return rest
    })

    return NextResponse.json({ success: true, chats: result })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar provider-chats:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
