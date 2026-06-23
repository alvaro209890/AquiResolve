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

// Banners do carrossel da Home do app (coleção `home_banners`) — um doc por banner.
// Escrita exclusiva via Admin SDK (as Firestore rules bloqueiam o client SDK), garantindo que
// só o painel publique banners. O app apenas lê (BannerRepository.kt). Cadastrar/editar/desativar
// um banner NÃO exige novo APK — o conteúdo é dado, não código.

const COLLECTION = 'home_banners'

const ACTION_TYPES = ['niche', 'service', 'combos', 'partners', 'cashback', 'url', 'none'] as const
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

// GET /api/banners — lista todos os banners ordenados por displayOrder.
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
    console.error('Erro ao listar banners:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/banners — cria ou atualiza (quando `id` é enviado) um banner.
export async function POST(request: NextRequest) {
  try {
    const actor = await authorizeAdminRequest(request)
    const db = getAdminFirestore()
    const input = (await request.json()) as BannerInput

    const imageUrl = String(input.imageUrl ?? '').trim()
    if (!imageUrl) {
      return NextResponse.json(
        { success: false, error: 'imageUrl é obrigatório (URL ou upload da imagem)' },
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
      // actionValue só faz sentido para niche/service/url; nos demais é ignorado pelo app.
      actionValue:
        actionType === 'niche' || actionType === 'service' || actionType === 'url'
          ? String(input.actionValue ?? '').trim()
          : '',
      backgroundColor: String(input.backgroundColor ?? '').trim(),
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

    if (isNew) {
      assertAdminPermission(actor, 'criarBanners')
      if (active) assertAdminPermission(actor, 'publicarBanners')
    } else {
      const currentSnap = await db.collection(COLLECTION).doc(id).get()
      if (!currentSnap.exists) {
        return NextResponse.json({ success: false, error: 'Banner não encontrado' }, { status: 404 })
      }
      const current = currentSnap.data() ?? {}
      const contentChanged =
        String(current.title ?? '') !== payload.title ||
        String(current.subtitle ?? '') !== payload.subtitle ||
        String(current.imageUrl ?? current.image ?? current.url ?? '') !== payload.imageUrl ||
        normalizeActionType(String(current.actionType ?? 'none')) !== payload.actionType ||
        String(current.actionValue ?? '') !== payload.actionValue ||
        String(current.backgroundColor ?? '') !== payload.backgroundColor ||
        Number(current.displayOrder ?? current.order ?? current.sortOrder ?? 0) !== payload.displayOrder
      const activeChanged = Boolean(current.active ?? current.isActive ?? current.enabled ?? true) !== active

      if (contentChanged || !activeChanged) assertAdminPermission(actor, 'editarBanners')
      if (activeChanged) assertAdminPermission(actor, 'publicarBanners')
    }

    const docPayload = isNew
      ? { ...payload, createdAt: admin.firestore.FieldValue.serverTimestamp() }
      : payload

    await db.collection(COLLECTION).doc(id).set(docPayload, { merge: true })

    await writeAdminAuditLog(actor, {
      action: isNew ? 'banner.create' : 'banner.update',
      resource: COLLECTION,
      resourceId: id,
      details: { title: payload.title, active: payload.active, actionType: payload.actionType },
    }).catch((auditError) => console.error('Falha ao registrar auditoria de banner:', auditError))

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar banner:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// DELETE /api/banners?id=xxx — remove um banner.
export async function DELETE(request: NextRequest) {
  try {
    const actor = await requireAdminPermission(request, 'excluirBanners')
    const db = getAdminFirestore()
    const id = request.nextUrl.searchParams.get('id')?.trim()
    if (!id) {
      return NextResponse.json({ success: false, error: 'id é obrigatório' }, { status: 400 })
    }

    await db.collection(COLLECTION).doc(id).delete()

    await writeAdminAuditLog(actor, {
      action: 'banner.delete',
      resource: COLLECTION,
      resourceId: id,
    }).catch((auditError) => console.error('Falha ao registrar auditoria de banner:', auditError))

    return NextResponse.json({ success: true, id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao remover banner:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
