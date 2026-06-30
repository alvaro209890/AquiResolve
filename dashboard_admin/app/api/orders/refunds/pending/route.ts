import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// GET /api/orders/refunds/pending
// Lista os pedidos com reembolso pendente (refundStatus == 'pending'), ou seja,
// pagos e cancelados/expirados que ainda aguardam o estorno na Pagar.me.
// Ordenado pela data da solicitação (mais antigo primeiro) — em memória, sem índice.
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    const db = getAdminFirestore()

    const snap = await db
      .collection('orders')
      .where('refundStatus', '==', 'pending')
      .get()

    const toMillis = (v: unknown): number => {
      if (!v) return 0
      // Firestore Timestamp (Admin SDK) tem toMillis(); fallback p/ Date/number
      const anyV = v as { toMillis?: () => number; seconds?: number }
      if (typeof anyV.toMillis === 'function') return anyV.toMillis()
      if (typeof anyV.seconds === 'number') return anyV.seconds * 1000
      const n = new Date(v as string).getTime()
      return Number.isNaN(n) ? 0 : n
    }

    const items = snap.docs
      .map((doc) => {
        const d = doc.data() || {}
        const amount =
          typeof d.finalPrice === 'number' && d.finalPrice > 0
            ? d.finalPrice
            : typeof d.estimatedPrice === 'number'
              ? d.estimatedPrice
              : 0
        return {
          id: doc.id,
          protocol: d.protocol ?? null,
          clientId: d.clientId ?? null,
          clientName: d.clientName ?? 'Cliente',
          serviceName: d.serviceName ?? d.serviceType ?? 'Serviço',
          amount,
          paymentStatus: d.paymentStatus ?? null,
          status: d.status ?? null,
          cancelledBy: d.cancelledBy ?? null,
          cancellationReason: d.cancellationReason ?? null,
          refundStatus: d.refundStatus ?? null,
          refundRequestedAtMs: toMillis(d.refundRequestedAt ?? d.cancelledAt ?? d.updatedAt),
          // Sinaliza se dá pra estornar pelo endpoint (precisa de transação Pagar.me).
          hasGatewayTransaction: Boolean(
            String(d.transactionId || d.gatewayOrderId || '').trim()
          ),
        }
      })
      .sort((a, b) => a.refundRequestedAtMs - b.refundRequestedAtMs)

    return NextResponse.json({ success: true, count: items.length, items })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao listar reembolsos pendentes:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
