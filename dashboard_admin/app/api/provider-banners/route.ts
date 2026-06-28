import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import {
  adminAuthorizationResponse,
  assertAdminPermission,
  authorizeAdminRequest,
  requireAdminPermission,
  writeAdminAuditLog,
} from '@/lib/server/admin-authorization'

// Banners do carrossel da Home do PRESTADOR (colecao provider_banners).
// Mesma estrutura dos banners do cliente (home_banners), colecao separada.

const COLLECTION = 'provider_banners'

const ACTION_TYPES = ['niche', 'service', 'url', 'none'] as const
type ActionType = (typeof ACTION_TYPES)[number]

interface BannerInput {
  id?: string
  title?: string
  subtitle?: string
  imageUrl?: string
  actionType?: string
  actionValue?: string
  backgroundColor?: string
  active?: boolean
  displayOrder?: number
}

function normalizeActionType(value: string | undefined): ActionType {
  const v = String(value ?? '').trim().toLowerCase()
  return (ACTION_TYPES as readonly string[]).includes(v) ? (v as ActionType) : 'none'
}

// GET /api/provider-banners
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'visualizarBanners')
    const db = getAdminFirestore()
    const snapshot = await db.collection(COLLECTION).get()

    const banners = snapshot.docs
      .map((doc) => {
        const data = doc.data()
        return {
          id: doc.id,
          title: String(data.title ?? ''),
          subtitle: String(data.subtitle ?? ''),
          imageUrl: String(data.imageUrl ?? data.image ?? data.url ?? ''),
          actionType: String(data.actionType ?? 'none'),
          actionValue: String(data.actionValue ?? ''),
          backgroundColor: String(data.backgroundColor ?? ''),
          active: Boolean(data.active ?? data.isActive ?? data.enabled ?? true),
          displayOrder: Number(data.displayOrder ?? data.order ?? data.sortOrder ?? 0),
        }
      })
      .sort((a, b) => a.displayOrder - b.displayOrder || a.title.localeCompare(b.title, 'pt-BR'))

    return NextResponse.json({ success: true, banners })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar banners de prestador:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/provider-banners
export async function POST(request: NextRequest) {
  try {
    const actor = await authorizeAdminRequest(request)
    const db = getAdminFirestore()
    const input = (await request.json()) as BannerInput

    const imageUrl = String(input.imageUrl ?? '').trim()
    if (!imageUrl) {
      return NextResponse.json(
        { success: false, error: 'imageUrl e obrigatorio' },
        { status: 400 }
      )
    }

    const actionType = normalizeActionType(input.actionType)
    const active = input.active !== false
    const order = Number.isFinite(Number(input.displayOrder)) ? Number(input.displayOrder) : 0

    const payload = {
      title: String(input.title ?? '').trim(),
      subtitle: String(input.subtitle ?? '').trim(),
      imageUrl,
      actionType,
      actionValue: actionType === 'niche' || actionType === 'service' || actionType === 'url'
        ? String(input.actionValue ?? '').trim() : '',
      backgroundColor: String(input.backgroundColor ?? '').trim(),
      active, isActive: active, enabled: active,
      displayOrder: order, order, sortOrder: order,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    const id = input.id?.trim() || db.collection(COLLECTION).doc().id
    const isNew = !input.id?.trim()

    if (isNew) {
      assertAdminPermission(actor, 'criarBanners')
      if (active) assertAdminPermission(actor, 'publicarBanners')
    }

    const docPayload = isNew
      ? { ...payload, createdAt: admin.firestore.FieldValue.serverTimestamp() }
      : payload

    await db.collection(COLLECTION).doc(id).set(docPayload, { merge: true })

    await writeAdminAuditLog(actor, {
      action: isNew ? 'provider_banner.create' : 'provider_banner.update',
      resource: COLLECTION,
      resourceId: id,
      details: { title: payload.title, active: payload.active },
    }).catch((e) => console.error('Falha ao auditar banner prestador:', e))

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar banner de prestador:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/provider-banners?id=xxx
export async function DELETE(request: NextRequest) {
  try {
    const actor = await requireAdminPermission(request, 'excluirBanners')
    const db = getAdminFirestore()
    const id = request.nextUrl.searchParams.get('id')?.trim()
    if (!id) {
      return NextResponse.json({ success: false, error: 'id e obrigatorio' }, { status: 400 })
    }

    await db.collection(COLLECTION).doc(id).delete()

    await writeAdminAuditLog(actor, {
      action: 'provider_banner.delete',
      resource: COLLECTION,
      resourceId: id,
    }).catch((e) => console.error('Falha ao auditar exclusao banner prestador:', e))

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao remover banner de prestador:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
