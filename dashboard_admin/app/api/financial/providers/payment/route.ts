import { NextRequest, NextResponse } from 'next/server'
import * as admin from 'firebase-admin'
import { adminApp } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import {
  amountToCents,
  buildOrderPayoutSnapshot,
  centsToAmount,
  readString,
} from '../_lib/payout'

export const dynamic = 'force-dynamic'

type ProviderPayoutStatus = 'pending' | 'partial' | 'paid'

interface PaymentRequest {
  providerId: string
  amount: number
  description?: string
  paymentMethod?: string
}

interface AllocationResult {
  orderId: string
  allocatedAmount: number
  allocatedCents: number
  previousPaidAmount: number
  previousPaidCents: number
  newPaidAmount: number
  newPaidCents: number
  remainingAmount: number
  remainingCents: number
  status: ProviderPayoutStatus
}

interface OrderCandidate {
  ref: FirebaseFirestore.DocumentReference
  orderId: string
  totalCommissionCents: number
  alreadyPaidCents: number
  remainingCents: number
  sortTimestampMs: number
}

interface TransactionResult {
  paymentId: string
  providerId: string
  providerUid: string
  providerName: string
  amount: number
  amountCents: number
  previousBalance: number
  previousBalanceCents: number
  newBalance: number
  newBalanceCents: number
  paymentMethod: string
  allocations: AllocationResult[]
  totalOrdersAffected: number
}

class HttpError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'HttpError'
    this.status = status
  }
}

const resolveProviderName = (
  _providerId: string,
  providerData: Record<string, unknown>
): string =>
  readString(providerData.nome) ||
  readString(providerData.name) ||
  readString(providerData.fullName)

const resolveProviderUid = (providerId: string, providerData: Record<string, unknown>): string =>
  readString(providerData.uid) || providerId

/**
 * POST /api/financial/providers/payment
 * Processa pagamento para um prestador com baixa por pedido.
 */
export async function POST(request: NextRequest) {
  try {
    const actor = await requireAdminPermission(request, 'operarFinanceiro')
    if (!adminApp) {
      return NextResponse.json(
        {
          success: false,
          error: 'Firebase Admin SDK não configurado',
        },
        { status: 500 }
      )
    }

    const body = (await request.json()) as PaymentRequest
    const providerId = readString(body.providerId)
    const requestedAmount = typeof body.amount === 'number' ? body.amount : Number(body.amount)
    const paymentMethod = readString(body.paymentMethod) || 'pix'

    if (!providerId || !Number.isFinite(requestedAmount) || requestedAmount <= 0) {
      return NextResponse.json(
        {
          success: false,
          error: 'Dados inválidos. providerId e amount são obrigatórios.',
        },
        { status: 400 }
      )
    }

    const amountCents = amountToCents(requestedAmount)
    if (amountCents <= 0) {
      return NextResponse.json(
        {
          success: false,
          error: 'Valor inválido para pagamento.',
        },
        { status: 400 }
      )
    }

    const db = adminApp.firestore()
    const paymentRef = db.collection('provider_payments').doc()
    const paymentId = paymentRef.id

    const result = await db.runTransaction(async (transaction): Promise<TransactionResult> => {
      const providerRef = db.collection('providers').doc(providerId)
      const ordersQuery = db.collection('orders')

      const [providerDoc, ordersSnapshot] = await Promise.all([
        transaction.get(providerRef),
        transaction.get(ordersQuery),
      ])

      const providerData = providerDoc.exists
        ? (providerDoc.data() as Record<string, unknown>)
        : {}

      const providerName = resolveProviderName(providerId, providerData)
      const providerUid = resolveProviderUid(providerId, providerData)
      const acceptedProviderIds = new Set<string>([providerId])
      const providerDocId = readString(providerData.id)
      if (providerDocId) {
        acceptedProviderIds.add(providerDocId)
      }

      const providerDataProviderId = readString(providerData.providerId)
      if (providerDataProviderId) {
        acceptedProviderIds.add(providerDataProviderId)
      }

      const providerDataProviderUid = readString(providerData.providerUid)
      if (providerDataProviderUid) {
        acceptedProviderIds.add(providerDataProviderUid)
      }

      const providerVerificationCode =
        readString(providerData.providerVerificationCode) ||
        readString(providerData.verificationCode) ||
        readString(providerData.verification_code)
      if (providerVerificationCode) {
        acceptedProviderIds.add(providerVerificationCode)
      }

      if (providerUid) {
        acceptedProviderIds.add(providerUid)
      }

      const candidates: OrderCandidate[] = []

      ordersSnapshot.forEach((orderDoc) => {
        const orderData = orderDoc.data() as Record<string, unknown>
        const payout = buildOrderPayoutSnapshot(orderDoc.id, orderData)

        if (
          !payout.eligible ||
          payout.remainingCents <= 0 ||
          !acceptedProviderIds.has(payout.providerId)
        ) {
          return
        }

        candidates.push({
          ref: orderDoc.ref,
          orderId: payout.orderId,
          totalCommissionCents: payout.totalCommissionCents,
          alreadyPaidCents: payout.alreadyPaidCents,
          remainingCents: payout.remainingCents,
          sortTimestampMs: payout.sortTimestampMs,
        })
      })

      if (candidates.length === 0) {
        throw new HttpError(400, 'Prestador sem saldo pendente para pagamento.')
      }

      candidates.sort((a, b) => {
        if (a.sortTimestampMs !== b.sortTimestampMs) {
          return a.sortTimestampMs - b.sortTimestampMs
        }
        return a.orderId.localeCompare(b.orderId)
      })

      const previousBalanceCents = candidates.reduce(
        (sum, candidate) => sum + candidate.remainingCents,
        0
      )

      if (amountCents > previousBalanceCents) {
        throw new HttpError(
          400,
          `Valor a pagar (R$ ${centsToAmount(amountCents).toFixed(2)}) excede o saldo disponível (R$ ${centsToAmount(previousBalanceCents).toFixed(2)}).`
        )
      }

      const allocations: AllocationResult[] = []
      let remainingToAllocate = amountCents

      for (const candidate of candidates) {
        if (remainingToAllocate <= 0) {
          break
        }

        const allocatedCents = Math.min(candidate.remainingCents, remainingToAllocate)
        if (allocatedCents <= 0) {
          continue
        }

        const previousPaidCents = candidate.alreadyPaidCents
        const newPaidCents = previousPaidCents + allocatedCents
        const remainingCents = Math.max(candidate.totalCommissionCents - newPaidCents, 0)

        const status: ProviderPayoutStatus =
          remainingCents === 0 ? 'paid' : newPaidCents > 0 ? 'partial' : 'pending'

        transaction.update(candidate.ref, {
          providerPayoutStatus: status,
          providerPayoutPaidAmount: centsToAmount(newPaidCents),
          providerPayoutPaidCents: newPaidCents,
          providerPayoutRemainingAmount: centsToAmount(remainingCents),
          providerPayoutRemainingCents: remainingCents,
          providerPayoutLastPaymentId: paymentId,
          providerPayoutUpdatedAt: admin.firestore.FieldValue.serverTimestamp(),
        })

        allocations.push({
          orderId: candidate.orderId,
          allocatedAmount: centsToAmount(allocatedCents),
          allocatedCents,
          previousPaidAmount: centsToAmount(previousPaidCents),
          previousPaidCents,
          newPaidAmount: centsToAmount(newPaidCents),
          newPaidCents,
          remainingAmount: centsToAmount(remainingCents),
          remainingCents,
          status,
        })

        remainingToAllocate -= allocatedCents
      }

      if (remainingToAllocate > 0) {
        throw new HttpError(500, 'Falha ao distribuir o pagamento pelos pedidos elegíveis.')
      }

      const newBalanceCents = previousBalanceCents - amountCents

      transaction.set(paymentRef, {
        paymentId,
        providerId,
        providerUid,
        providerName,
        amount: centsToAmount(amountCents),
        amountCents,
        previousBalance: centsToAmount(previousBalanceCents),
        previousBalanceCents,
        newBalance: centsToAmount(newBalanceCents),
        newBalanceCents,
        paymentMethod,
        description:
          readString(body.description) ||
          `Pagamento para ${providerName || providerUid || providerId} no valor de R$ ${centsToAmount(amountCents).toFixed(2)}`,
        status: 'completed',
        allocations,
        totalOrdersAffected: allocations.length,
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
        processedBy: actor.uid,
        processedByEmail: actor.email,
      })

      return {
        paymentId,
        providerId,
        providerUid,
        providerName,
        amount: centsToAmount(amountCents),
        amountCents,
        previousBalance: centsToAmount(previousBalanceCents),
        previousBalanceCents,
        newBalance: centsToAmount(newBalanceCents),
        newBalanceCents,
        paymentMethod,
        allocations,
        totalOrdersAffected: allocations.length,
      }
    })

    return NextResponse.json({
      success: true,
      message: 'Pagamento processado com sucesso',
      data: result,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    if (error instanceof HttpError) {
      return NextResponse.json(
        {
          success: false,
          error: error.message,
        },
        { status: error.status }
      )
    }

    console.error('Erro ao processar pagamento:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao processar pagamento',
        details: error instanceof Error ? error.message : 'Erro desconhecido',
      },
      { status: 500 }
    )
  }
}
