import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// Combos promocionais (vitrine) da Home do app — coleção `home_combos`, um doc por combo.
// Escrita exclusiva via Admin SDK (as Firestore rules bloqueiam o client SDK). O app apenas lê
// (ComboRepository.kt). Cadastrar/editar/desativar um combo NÃO exige novo APK.
//
// IMPORTANTE — preço é exibição, não cobrança: fullPrice/promoPrice/savings/discountPercent são
// curadoria/vitrine. O valor cobrado vem do carrinho→backend (catalog_services + PromotionManager),
// recalculado pelas categorias dos itens. Por isso o combo deve conter itens cujos nichos disparem
// o desconto anunciado.

const COLLECTION = 'home_combos'

interface ComboItemInput {
  niche?: string
  serviceName?: string
  serviceId?: string
}

interface ComboInput {
  id?: string
  name?: string
  description?: string
  imageUrl?: string
  items?: ComboItemInput[]
  fullPrice?: number
  promoPrice?: number
  savings?: number
  discountPercent?: number
  active?: boolean
  displayOrder?: number
}

function roundMoney(value: number): number {
  return Math.round(Number(value) * 100) / 100
}

function clampPercent(value: number): number {
  if (!Number.isFinite(value)) return 0
  return Math.min(100, Math.max(0, Math.round(value)))
}

function normalizeItems(items: ComboItemInput[] | undefined) {
  if (!Array.isArray(items)) return []
  return items
    .map((it) => ({
      niche: String(it.niche ?? '').trim(),
      serviceName: String(it.serviceName ?? '').trim(),
      serviceId: String(it.serviceId ?? '').trim(),
    }))
    .filter((it) => it.niche && it.serviceName)
}

// GET /api/combos — lista todos os combos ordenados por displayOrder.
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarCombos')
    const db = getAdminFirestore()
    const snapshot = await db.collection(COLLECTION).get()

    const combos = snapshot.docs
      .map((doc) => {
        const data = doc.data()
        const items = Array.isArray(data.items) ? data.items : []
        return {
          id: doc.id,
          name: String(data.name ?? data.title ?? ''),
          description: String(data.description ?? ''),
          imageUrl: String(data.imageUrl ?? data.image ?? data.url ?? ''),
          items: items.map((it: ComboItemInput) => ({
            niche: String(it.niche ?? ''),
            serviceName: String(it.serviceName ?? ''),
            serviceId: String(it.serviceId ?? ''),
          })),
          fullPrice: Number(data.fullPrice ?? 0),
          promoPrice: Number(data.promoPrice ?? 0),
          savings: Number(data.savings ?? 0),
          discountPercent: Number(data.discountPercent ?? 0),
          active: Boolean(data.active ?? data.isActive ?? data.enabled ?? true),
          displayOrder: Number(data.displayOrder ?? data.order ?? data.sortOrder ?? 0),
        }
      })
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))

    return NextResponse.json({ success: true, combos })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar combos:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/combos — cria ou atualiza (quando `id` é enviado) um combo.
// O servidor recalcula promoPrice/savings a partir de fullPrice + discountPercent para evitar
// divergência (fullPrice deve ser a soma dos preços do catálogo, calculada no cliente).
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarCombos')
    const db = getAdminFirestore()
    const input = (await request.json()) as ComboInput

    const name = String(input.name ?? '').trim()
    if (!name) {
      return NextResponse.json({ success: false, error: 'Nome do combo é obrigatório' }, { status: 400 })
    }

    const items = normalizeItems(input.items)
    if (items.length < 2) {
      return NextResponse.json(
        { success: false, error: 'O combo precisa de pelo menos 2 serviços' },
        { status: 400 }
      )
    }

    const fullPrice = Math.max(0, roundMoney(Number(input.fullPrice) || 0))
    const discountPercent = clampPercent(Number(input.discountPercent) || 0)
    // promoPrice/savings derivados (não confiar no que vem do cliente).
    const promoPrice = roundMoney(fullPrice * (1 - discountPercent / 100))
    const savings = roundMoney(fullPrice - promoPrice)

    const active = input.active !== false
    const order = Number.isFinite(Number(input.displayOrder)) ? Number(input.displayOrder) : 0

    const payload = {
      name,
      description: String(input.description ?? '').trim(),
      imageUrl: String(input.imageUrl ?? '').trim(),
      items,
      fullPrice,
      promoPrice,
      savings,
      discountPercent,
      active,
      isActive: active,
      enabled: active,
      displayOrder: order,
      order,
      sortOrder: order,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    const id = input.id?.trim() || db.collection(COLLECTION).doc().id
    const isNew = !input.id?.trim()

    const docPayload = isNew
      ? { ...payload, createdAt: admin.firestore.FieldValue.serverTimestamp() }
      : payload

    await db.collection(COLLECTION).doc(id).set(docPayload, { merge: true })

    return NextResponse.json({ success: true, id, promoPrice, savings })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar combo:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/combos?id=xxx — remove um combo.
export async function DELETE(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarCombos')
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
    console.error('Erro ao remover combo:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
