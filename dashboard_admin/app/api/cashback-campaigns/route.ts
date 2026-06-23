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

function serializeCampaign(doc: admin.firestore.QueryDocumentSnapshot | admin.firestore.DocumentSnapshot) {
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

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarAquicash')
    const db = getAdminFirestore()
    const snap = await db.collection(COLLECTION).orderBy('createdAt', 'desc').get()
    return NextResponse.json({
      success: true,
      campaigns: snap.docs.map(serializeCampaign),
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar campanhas de cashback:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gerenciarAquicash')
    const db = getAdminFirestore()
    const body = await request.json()
    const name = String(body.name || '').trim()
    if (!name) {
      return NextResponse.json({ success: false, error: 'Nome da campanha é obrigatório' }, { status: 400 })
    }

    const bonusPercentage = numberFrom(body.bonusPercentage)
    if (bonusPercentage <= 0 || bonusPercentage > 100) {
      return NextResponse.json({ success: false, error: 'Bônus deve estar entre 0,01% e 100%' }, { status: 400 })
    }

    const startsAt = dateOrNull(body.startsAt)
    const endsAt = dateOrNull(body.endsAt)
    if (startsAt && endsAt && startsAt.toMillis() > endsAt.toMillis()) {
      return NextResponse.json({ success: false, error: 'Data inicial deve ser anterior à data final' }, { status: 400 })
    }

    const now = admin.firestore.FieldValue.serverTimestamp()
    const ref = await db.collection(COLLECTION).add({
      name,
      enabled: body.enabled !== false,
      bonusPercentage,
      maxCashbackPerOrder: Math.max(0, numberFrom(body.maxCashbackPerOrder)),
      startsAt,
      endsAt,
      createdAt: now,
      updatedAt: now,
    })

    const doc = await ref.get()
    return NextResponse.json({
      success: true,
      campaign: serializeCampaign(doc),
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao criar campanha de cashback:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
