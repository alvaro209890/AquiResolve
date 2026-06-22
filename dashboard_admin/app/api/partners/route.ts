import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

// Parceiros patrocinadores da Home do app — coleção `partners`, um doc por parceiro.
// Escrita exclusiva via Admin SDK (as Firestore rules bloqueiam o client SDK). O app apenas lê
// (PartnerRepository.kt). Cadastrar/editar/desativar um parceiro NÃO exige novo APK.

const COLLECTION = 'partners'

const BENEFIT_TYPES = ['discount', 'cashback', 'coupon', 'link'] as const
type BenefitType = (typeof BENEFIT_TYPES)[number]

interface PartnerInput {
  id?: string
  name?: string
  logoUrl?: string
  bannerUrl?: string
  description?: string
  benefitType?: string
  benefitLabel?: string
  couponCode?: string
  url?: string
  active?: boolean
  displayOrder?: number
}

function normalizeBenefitType(value: string | undefined): BenefitType {
  const v = String(value ?? '').trim().toLowerCase()
  return (BENEFIT_TYPES as readonly string[]).includes(v) ? (v as BenefitType) : 'link'
}

// GET /api/partners — lista todos os parceiros ordenados por displayOrder.
export async function GET() {
  try {
    const db = getAdminFirestore()
    const snapshot = await db.collection(COLLECTION).get()

    const partners = snapshot.docs
      .map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          name: String(data.name ?? data.title ?? ''),
          logoUrl: String(data.logoUrl ?? data.logo ?? data.imageUrl ?? ''),
          bannerUrl: String(data.bannerUrl ?? data.banner ?? ''),
          description: String(data.description ?? ''),
          benefitType: String(data.benefitType ?? 'link'),
          benefitLabel: String(data.benefitLabel ?? data.benefit ?? ''),
          couponCode: String(data.couponCode ?? data.coupon ?? ''),
          url: String(data.url ?? data.link ?? ''),
          active: Boolean(data.active ?? data.isActive ?? data.enabled ?? true),
          displayOrder: Number(data.displayOrder ?? data.order ?? data.sortOrder ?? 0),
        }
      })
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))

    return NextResponse.json({ success: true, partners })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar parceiros:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/partners — cria ou atualiza (quando `id` é enviado) um parceiro.
export async function POST(request: NextRequest) {
  try {
    const db = getAdminFirestore()
    const input = (await request.json()) as PartnerInput

    const name = String(input.name ?? '').trim()
    if (!name) {
      return NextResponse.json({ success: false, error: 'Nome do parceiro é obrigatório' }, { status: 400 })
    }

    const logoUrl = String(input.logoUrl ?? '').trim()
    if (!logoUrl) {
      return NextResponse.json(
        { success: false, error: 'Logo é obrigatório (URL ou upload da imagem)' },
        { status: 400 }
      )
    }

    const benefitType = normalizeBenefitType(input.benefitType)
    // couponCode só faz sentido para benefitType=coupon; nos demais é ignorado pelo app.
    const couponCode = benefitType === 'coupon' ? String(input.couponCode ?? '').trim() : ''
    const active = input.active !== false
    const order = Number.isFinite(Number(input.displayOrder)) ? Number(input.displayOrder) : 0

    const payload = {
      name,
      logoUrl,
      bannerUrl: String(input.bannerUrl ?? '').trim(),
      description: String(input.description ?? '').trim(),
      benefitType,
      benefitLabel: String(input.benefitLabel ?? '').trim(),
      couponCode,
      url: String(input.url ?? '').trim(),
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

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar parceiro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/partners?id=xxx — remove um parceiro.
export async function DELETE(request: NextRequest) {
  try {
    const db = getAdminFirestore()
    const id = request.nextUrl.searchParams.get('id')?.trim()
    if (!id) {
      return NextResponse.json({ success: false, error: 'id é obrigatório' }, { status: 400 })
    }

    await db.collection(COLLECTION).doc(id).delete()

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao remover parceiro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
