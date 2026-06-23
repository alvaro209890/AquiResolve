import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/financial/settlements-summary
// Agrega order_settlements via Admin SDK (a coleção tem read: if isAdmin() nas regras,
// então o client SDK do painel não consegue ler diretamente sem custom claim).
// Devolve os totais usados nos KPIs de Cashback Distribuído e Comissões Liquidadas.
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const db = getAdminFirestore()
    const snap = await db.collection('order_settlements').get()

    let totalProviderCommission = 0
    let totalCashbackDistributed = 0
    snap.forEach((doc) => {
      const data = doc.data() || {}
      totalProviderCommission += Number(data.providerCommission) || 0
      totalCashbackDistributed += Number(data.cashbackAmount) || 0
    })

    return NextResponse.json({
      success: true,
      count: snap.size,
      totalProviderCommission,
      totalCashbackDistributed,
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao agregar order_settlements:', message)
    return NextResponse.json(
      { success: false, error: message, count: 0, totalProviderCommission: 0, totalCashbackDistributed: 0 },
      { status: 500 }
    )
  }
}
