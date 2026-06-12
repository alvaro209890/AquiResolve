import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

const CONFIG_COLLECTION = 'app_config'
const CONFIG_DOC = 'cashback'

// GET /api/cashback-config — lê configuração de cashback
export async function GET() {
  try {
    const db = getAdminFirestore()
    const snap = await db.collection(CONFIG_COLLECTION).doc(CONFIG_DOC).get()

    if (!snap.exists) {
      return NextResponse.json({
        success: true,
        config: null,
        message: 'Configuração ainda não salva — usando valores padrão do app',
      })
    }

    return NextResponse.json({ success: true, config: snap.data() })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao ler cashback config:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/cashback-config — salva configuração de cashback (Admin SDK bypassa a regra allow write: if false)
export async function POST(request: NextRequest) {
  try {
    const db = getAdminFirestore()
    const body = await request.json()

    // Validações básicas
    const {
      enabled,
      earnPercentage,
      allowRedeem,
      maxRedeemPercentage,
      tiersEnabled,
      bronzeRate,
      silverRate,
      goldRate,
      silverThreshold,
      goldThreshold,
      activePhase,
      directDiscountEnabled,
      directDiscount2,
      directDiscount3,
      directDiscount4Plus,
      combosEnabled,
      comboEletricaHidraulicaInstalacoes,
      comboEletricaHidraulica,
      comboInstalacoesHidraulica,
      comboVeiculos,
    } = body

    const config: Record<string, unknown> = {
      enabled: Boolean(enabled),
      earnPercentage: Number(earnPercentage) || 5,
      allowRedeem: Boolean(allowRedeem),
      maxRedeemPercentage: Number(maxRedeemPercentage) || 100,
      tiersEnabled: Boolean(tiersEnabled),
      bronzeRate: Number(bronzeRate) || 3,
      silverRate: Number(silverRate) || 5,
      goldRate: Number(goldRate) || 8,
      silverThreshold: Number(silverThreshold) || 500,
      goldThreshold: Number(goldThreshold) || 1500,
      activePhase: activePhase === 'launch' ? 'launch' : 'growth',
      directDiscountEnabled: Boolean(directDiscountEnabled),
      directDiscount2: Number(directDiscount2) || 5,
      directDiscount3: Number(directDiscount3) || 10,
      directDiscount4Plus: Number(directDiscount4Plus) || 15,
      combosEnabled: Boolean(combosEnabled),
      comboEletricaHidraulicaInstalacoes: Number(comboEletricaHidraulicaInstalacoes) || 15,
      comboEletricaHidraulica: Number(comboEletricaHidraulica) || 10,
      comboInstalacoesHidraulica: Number(comboInstalacoesHidraulica) || 10,
      comboVeiculos: Number(comboVeiculos) || 15,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }

    await db.collection(CONFIG_COLLECTION).doc(CONFIG_DOC).set(config, { merge: true })

    return NextResponse.json({
      success: true,
      message: 'Configuração de cashback salva com sucesso',
      config,
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao salvar cashback config:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
