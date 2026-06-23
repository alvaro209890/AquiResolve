import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import {
  adminAuthorizationResponse,
  requireAdminPermission,
  requireAnyAdminPermission,
} from '@/lib/server/admin-authorization'

// Serviços do catálogo do app (coleção `catalog_services`) — um doc por serviço,
// com nicho, valor ao cliente e % do prestador. Escrita exclusiva via Admin SDK
// (as Firestore rules bloqueiam o client SDK), evitando que o APK altere preços.
// A leitura/real-time do painel é feita via client SDK (onSnapshot).

const COLLECTION = 'catalog_services'

function normalizeSlug(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

function roundMoney(value: number): number {
  return Math.round(Number(value) * 100) / 100
}

function clampPercent(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, value))
}

interface ServiceInput {
  id?: string
  niche?: string
  nicheSlug?: string
  name?: string
  slug?: string
  description?: string
  estimatedTime?: string
  estimatedPrice?: number
  providerCommissionPercent?: number
  isConsult?: boolean
  active?: boolean
  displayOrder?: number
}

// GET /api/catalog/services?niche=Elétrica — lista os serviços (todos ou de um nicho).
export async function GET(request: NextRequest) {
  try {
    await requireAnyAdminPermission(request, ['gerenciarCatalogo', 'gerenciarCombos'])
    const db = getAdminFirestore()
    const niche = request.nextUrl.searchParams.get('niche')?.trim()

    const ref = niche
      ? db.collection(COLLECTION).where('niche', '==', niche)
      : db.collection(COLLECTION)
    const snapshot = await ref.get()

    const services = snapshot.docs
      .map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          niche: String(data.niche ?? ''),
          nicheSlug: String(data.nicheSlug ?? ''),
          name: String(data.name ?? data.title ?? data.label ?? ''),
          slug: String(data.slug ?? ''),
          description: String(data.description ?? ''),
          estimatedTime: String(data.estimatedTime ?? ''),
          estimatedPrice: Number(data.estimatedPrice ?? 0),
          providerCommissionPercent: Number(data.providerCommissionPercent ?? 0),
          providerCommission: Number(data.providerCommission ?? 0),
          isConsult: Boolean(data.isConsult ?? false),
          active: Boolean(data.active ?? data.isActive ?? data.enabled ?? true),
          displayOrder: Number(data.displayOrder ?? data.order ?? data.sortOrder ?? 0),
        }
      })
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))

    return NextResponse.json({ success: true, services })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar serviços do catálogo:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/catalog/services — cria ou atualiza (quando `id` é enviado) um serviço.
// O servidor é o único lugar que calcula providerCommission a partir do percentual,
// garantindo que app/backend continuem consumindo o valor absoluto em R$.
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarCatalogo')
    const db = getAdminFirestore()
    const input = (await request.json()) as ServiceInput

    const niche = String(input.niche ?? '').trim()
    const name = String(input.name ?? '').trim()
    if (!niche) {
      return NextResponse.json({ success: false, error: 'Nicho é obrigatório' }, { status: 400 })
    }
    if (!name) {
      return NextResponse.json({ success: false, error: 'Nome do serviço é obrigatório' }, { status: 400 })
    }

    const slug = normalizeSlug(input.slug || name)
    const nicheSlug = normalizeSlug(input.nicheSlug || niche)
    const isConsult = input.isConsult === true
    const active = input.active !== false
    const order = Number.isFinite(Number(input.displayOrder)) ? Number(input.displayOrder) : 0

    const estimatedPrice = isConsult ? 0 : Math.max(0, roundMoney(Number(input.estimatedPrice) || 0))
    const percent = isConsult ? 0 : clampPercent(Number(input.providerCommissionPercent) || 0)
    const providerCommission = roundMoney(estimatedPrice * percent / 100)

    const payload = {
      niche,
      nicheSlug,
      name,
      title: name,
      label: name,
      slug,
      description: String(input.description ?? '').trim(),
      estimatedTime: String(input.estimatedTime ?? '').trim(),
      estimatedPrice,
      providerCommissionPercent: percent,
      providerCommission,
      isConsult,
      active,
      isActive: active,
      enabled: active,
      displayOrder: order,
      order,
      sortOrder: order,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    const id = input.id?.trim() || `${nicheSlug}__${slug}`
    const isNew = !input.id?.trim()

    const docPayload = isNew
      ? { ...payload, createdAt: admin.firestore.FieldValue.serverTimestamp() }
      : payload

    await db.collection(COLLECTION).doc(id).set(docPayload, { merge: true })

    return NextResponse.json({ success: true, id, providerCommission })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar serviço do catálogo:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/catalog/services?id=xxx — remove um serviço do catálogo.
export async function DELETE(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarCatalogo')
    const db = getAdminFirestore()
    const id = request.nextUrl.searchParams.get('id')?.trim()
    if (!id) {
      return NextResponse.json({ success: false, error: 'id é obrigatório' }, { status: 400 })
    }

    await db.collection(COLLECTION).doc(id).delete()

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao remover serviço do catálogo:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
