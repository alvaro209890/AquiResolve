import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/provider-chats/directory?search=&limit=50
// Lista os prestadores (id, nome, email) para o admin INICIAR uma conversa nova — já que o doc
// em `provider_chats` só nasce na primeira mensagem. Filtra por busca em memória.

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'controle')
    const db = getAdminFirestore()
    const { searchParams } = new URL(request.url)
    const search = (searchParams.get('search') ?? '').trim().toLowerCase()
    const limit = Math.min(parseInt(searchParams.get('limit') ?? '50', 10) || 50, 200)

    const snap = await db.collection('providers').get()

    let providers = snap.docs.map((d) => {
      const data = d.data() as Record<string, unknown>
      const name =
        (data.fullName as string) ??
        (data.name as string) ??
        (data.nome as string) ??
        (data.displayName as string) ??
        (data.companyName as string) ??
        'Prestador'
      return {
        id: d.id,
        name,
        email: (data.email as string) ?? '',
        active: data.isActive !== false,
      }
    })

    if (search) {
      providers = providers.filter(
        (p) =>
          p.name.toLowerCase().includes(search) ||
          p.email.toLowerCase().includes(search) ||
          p.id.toLowerCase().includes(search)
      )
    }

    providers.sort((a, b) => a.name.localeCompare(b.name, 'pt-BR'))

    return NextResponse.json({ success: true, providers: providers.slice(0, limit) })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar diretório de prestadores:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
