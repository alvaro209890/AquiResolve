import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

const COLLECTION = 'cashback_campaigns'

function numberFrom(value: unknown, fallback = 0) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

function dateOrNull(value: unknown) {
  if (!value || typeof value !== 'string') return null
  const date = new Date(value)
  return Number.isFinite(date.getTime()) ? admin.firestore.Timestamp.fromDate(date) : null
}

function serializeCampaign(doc: admin.firestore.DocumentSnapshot) {
  const data = doc.data() || {}
  return {
    id: doc.id,
    ...data,
    startsAt: data.startsAt?.toDate?.()?.toISOString?.() ?? null,
    endsAt: data.endsAt?.toDate?.()?.toISOString?.() ?? null,
    createdAt: data.createdAt?.toDate?.()?.toISOString?.() ?? null,
    updatedAt: data.updatedAt?.toDate?.()?.toISOString?.() ?? null,
  }
}

export async function PATCH(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    await requireAdminPermission(request, 'gerenciarAquicash')
    const db = getAdminFirestore()
    const body = await request.json()
    const { id } = await params
    const ref = db.collection(COLLECTION).doc(id)
    const snap = await ref.get()
    if (!snap.exists) {
      return NextResponse.json({ success: false, error: 'Campanha não encontrada' }, { status: 404 })
    }

    const update: Record<string, unknown> = {
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    if ('name' in body) {
      const name = String(body.name || '').trim()
      if (!name) {
        return NextResponse.json({ success: false, error: 'Nome da campanha é obrigatório' }, { status: 400 })
      }
      update.name = name
    }

    if ('enabled' in body) update.enabled = body.enabled === true

    if ('bonusPercentage' in body) {
      const bonusPercentage = numberFrom(body.bonusPercentage)
      if (bonusPercentage <= 0 || bonusPercentage > 100) {
        return NextResponse.json({ success: false, error: 'Bônus deve estar entre 0,01% e 100%' }, { status: 400 })
      }
      update.bonusPercentage = bonusPercentage
    }

    if ('maxCashbackPerOrder' in body) {
      update.maxCashbackPerOrder = Math.max(0, numberFrom(body.maxCashbackPerOrder))
    }

    if ('startsAt' in body) update.startsAt = dateOrNull(body.startsAt)
    if ('endsAt' in body) update.endsAt = dateOrNull(body.endsAt)

    const startsAt = 'startsAt' in update ? update.startsAt as admin.firestore.Timestamp | null : snap.data()?.startsAt
    const endsAt = 'endsAt' in update ? update.endsAt as admin.firestore.Timestamp | null : snap.data()?.endsAt
    if (startsAt && endsAt && startsAt.toMillis() > endsAt.toMillis()) {
      return NextResponse.json({ success: false, error: 'Data inicial deve ser anterior à data final' }, { status: 400 })
    }

    await ref.update(update)
    const fresh = await ref.get()
    return NextResponse.json({ success: true, campaign: serializeCampaign(fresh) })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao atualizar campanha de cashback:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

export async function DELETE(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    await requireAdminPermission(request, 'gerenciarAquicash')
    const db = getAdminFirestore()
    const { id } = await params
    await db.collection(COLLECTION).doc(id).delete()
    return NextResponse.json({ success: true })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao remover campanha de cashback:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
