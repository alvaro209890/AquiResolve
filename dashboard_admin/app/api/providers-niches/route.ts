import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/providers-niches
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'aprovarPrestadores')
    const db = getAdminFirestore()
    const { searchParams } = new URL(request.url)
    
    const niche = searchParams.get('niche') ?? 'Todos'
    const name = searchParams.get('name')?.toLowerCase() ?? ''
    const statusFilter = searchParams.get('status') ?? 'Todos'
    const verification = searchParams.get('verification') ?? 'Todos'
    const page = parseInt(searchParams.get('page') ?? '1', 10)
    const limit = parseInt(searchParams.get('limit') ?? '20', 10)

    let query = db.collection('providers') as admin.firestore.Query

    // Filtrar no Firestore onde for possível para reduzir payload
    if (niche !== 'Todos') {
      query = query.where('services', 'array-contains', niche)
    }

    const snap = await query.get()
    
    // Mapeamento
    let providers = snap.docs.map(doc => {
      const data = doc.data()
      return {
        id: doc.id,
        fullName: data.fullName || '',
        email: data.email || '',
        phone: data.phone || '',
        cpf: data.cpf || '',
        services: data.services || [],
        rating: typeof data.rating === 'number' ? data.rating : 0,
        totalRatings: typeof data.totalRatings === 'number' ? data.totalRatings : 0,
        isActive: data.isActive === true,
        verificationStatus: data.verificationStatus || 'pending',
        createdAt: data.createdAt,
        profileImageUrl: data.profileImageUrl || ''
      }
    })

    // Filtros em memória
    if (name) {
      providers = providers.filter(p => p.fullName.toLowerCase().includes(name))
    }
    
    if (statusFilter !== 'Todos') {
      const isActive = statusFilter === 'Ativo'
      providers = providers.filter(p => p.isActive === isActive)
    }
    
    if (verification !== 'Todos') {
      const mapVerification = (v: string) => {
        if (v === 'Pendente') return 'pending'
        if (v === 'Aprovado') return 'approved'
        if (v === 'Rejeitado') return 'rejected'
        return v
      }
      providers = providers.filter(p => p.verificationStatus === mapVerification(verification))
    }

    // Ordenação em memória (evitar composite index)
    const toMillis = (v: any) => {
      if (v && typeof v.toMillis === 'function') return v.toMillis()
      if (v && typeof v._seconds === 'number') return v._seconds * 1000
      const t = new Date(v).getTime()
      return Number.isNaN(t) ? 0 : t
    }
    
    providers.sort((a, b) => toMillis(b.createdAt) - toMillis(a.createdAt))

    // Paginação
    const total = providers.length
    const start = (page - 1) * limit
    const paginated = providers.slice(start, start + limit)

    return NextResponse.json({ 
      success: true, 
      providers: paginated,
      total,
      page,
      limit
    })
    
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
