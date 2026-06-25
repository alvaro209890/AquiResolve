import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

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
  whatsapp?: string
  instagram?: string
  rotationSeconds?: number
  dailyImpressionCap?: number
  startDate?: string
  campaignDays?: number
  campaignValue?: number
  active?: boolean
  displayOrder?: number
}

function normalizeBenefitType(value: string | undefined): BenefitType {
  const v = String(value ?? '').trim().toLowerCase()
  return (BENEFIT_TYPES as readonly string[]).includes(v) ? (v as BenefitType) : 'link'
}

// Segundos por banner: 3–20 (padrão 6). Clientes/dia: ≥1 (padrão 10). Dias de campanha: ≥0 (0 = sem fim).
function clampInt(value: unknown, min: number, max: number, fallback: number): number {
  const n = Math.floor(Number(value))
  if (!Number.isFinite(n)) return fallback
  return Math.min(Math.max(n, min), max)
}

// Data de início no formato yyyy-MM-dd (ou vazio).
function normalizeStartDate(value: string | undefined): string {
  const v = String(value ?? '').trim()
  return /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : ''
}

// Valor pago pela campanha (R$). Aceita número ou string com vírgula/ponto; nunca negativo.
function normalizeMoney(value: unknown): number {
  if (typeof value === 'number') return Number.isFinite(value) && value > 0 ? Math.round(value * 100) / 100 : 0
  const raw = String(value ?? '').trim().replace(/[^\d,.-]/g, '')
  if (!raw) return 0
  // "1.500,00" -> "1500.00" ; "1500.50" -> "1500.50"
  const normalized = raw.includes(',') ? raw.replace(/\./g, '').replace(',', '.') : raw
  const n = Number(normalized)
  return Number.isFinite(n) && n > 0 ? Math.round(n * 100) / 100 : 0
}

// GET /api/partners — lista todos os parceiros ordenados por displayOrder.
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarParceiros')
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
          whatsapp: String(data.whatsapp ?? data.whatsApp ?? data.phone ?? ''),
          instagram: String(data.instagram ?? data.instagramUrl ?? ''),
          rotationSeconds: Number(data.rotationSeconds ?? data.rotateSeconds ?? 6),
          dailyImpressionCap: Number(data.dailyImpressionCap ?? data.dailyCap ?? data.impressionCap ?? 10),
          startDate: String(data.startDate ?? data.startsAt ?? ''),
          campaignDays: Number(data.campaignDays ?? data.durationDays ?? 0),
          campaignValue: Number(data.campaignValue ?? data.campaignAmount ?? data.amountPaid ?? 0),
          active: Boolean(data.active ?? data.isActive ?? data.enabled ?? true),
          displayOrder: Number(data.displayOrder ?? data.order ?? data.sortOrder ?? 0),
        }
      })
      .sort((a, b) => a.displayOrder - b.displayOrder || a.name.localeCompare(b.name, 'pt-BR'))

    return NextResponse.json({ success: true, partners })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar parceiros:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/partners — cria ou atualiza (quando `id` é enviado) um parceiro.
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarParceiros')
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

    // WhatsApp só dígitos; regras de exibição com clamps; data de início validada.
    const whatsapp = String(input.whatsapp ?? '').replace(/\D/g, '')
    const rotationSeconds = clampInt(input.rotationSeconds, 3, 20, 6)
    const dailyImpressionCap = clampInt(input.dailyImpressionCap, 1, 1_000_000, 10)
    const campaignDays = clampInt(input.campaignDays, 0, 3650, 30)
    const campaignValue = normalizeMoney(input.campaignValue)
    const startDate = normalizeStartDate(input.startDate)

    const payload = {
      name,
      logoUrl,
      bannerUrl: String(input.bannerUrl ?? '').trim(),
      description: String(input.description ?? '').trim(),
      benefitType,
      benefitLabel: String(input.benefitLabel ?? '').trim(),
      couponCode,
      url: String(input.url ?? '').trim(),
      whatsapp,
      instagram: String(input.instagram ?? '').trim(),
      rotationSeconds,
      dailyImpressionCap,
      startDate,
      campaignDays,
      campaignValue,
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
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar parceiro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/partners?id=xxx — remove um parceiro.
export async function DELETE(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarParceiros')
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
    console.error('Erro ao remover parceiro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
