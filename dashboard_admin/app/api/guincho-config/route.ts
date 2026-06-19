import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

const CONFIG_COLLECTION = 'app_config'
const CONFIG_DOC = 'guincho'

const DEFAULTS = {
  enabled: true,
  baseFee: 180,
  pricePerKm: 3.9,
  providerPercent: 70,
  minKm: 0,
}

// GET /api/guincho-config — lê a configuração de precificação do guincho
export async function GET() {
  try {
    const db = getAdminFirestore()
    const snap = await db.collection(CONFIG_COLLECTION).doc(CONFIG_DOC).get()

    if (!snap.exists) {
      return NextResponse.json({
        success: true,
        config: DEFAULTS,
        message: 'Configuração ainda não salva — usando valores padrão do app',
      })
    }

    return NextResponse.json({ success: true, config: { ...DEFAULTS, ...snap.data() } })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao ler config do guincho:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/guincho-config — salva a configuração (Admin SDK bypassa allow write: if false)
export async function POST(request: NextRequest) {
  try {
    const db = getAdminFirestore()
    const body = await request.json()

    const clampPercent = (v: unknown) => {
      const n = Number(v)
      if (!Number.isFinite(n)) return DEFAULTS.providerPercent
      return Math.min(100, Math.max(0, n))
    }
    const nonNeg = (v: unknown, fallback: number) => {
      const n = Number(v)
      return Number.isFinite(n) && n >= 0 ? n : fallback
    }

    const config = {
      enabled: body.enabled !== false,
      baseFee: nonNeg(body.baseFee, DEFAULTS.baseFee),
      pricePerKm: nonNeg(body.pricePerKm, DEFAULTS.pricePerKm),
      providerPercent: clampPercent(body.providerPercent),
      minKm: nonNeg(body.minKm, DEFAULTS.minKm),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    await db.collection(CONFIG_COLLECTION).doc(CONFIG_DOC).set(config, { merge: true })

    return NextResponse.json({
      success: true,
      message: 'Configuração do guincho salva com sucesso',
      config,
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar config do guincho:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
