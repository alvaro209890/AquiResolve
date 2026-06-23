import { NextResponse } from 'next/server'
import { adminApp } from '@/lib/firebase-admin'
import type { NextRequest } from 'next/server'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'
import {
  buildOrderPayoutSnapshot,
  centsToAmount,
  readString,
  toIsoDate,
} from './_lib/payout'

export const dynamic = 'force-dynamic'

interface ProviderBilling {
  id: string
  uid: string
  nome?: string
  phone?: string
  email?: string
  pixKey?: string
  pixKeyType?: string
  totalEarnings: number
  totalEarningsCents: number
  totalJobs?: number
  eligibleOrdersCount: number
  pendingOrdersCount: number
  isActive?: boolean
  isVerified?: boolean
  verificationStatus?: string
  updatedAt?: string
}

const getNestedRecord = (value: unknown): Record<string, unknown> => {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return {}
  }
  return value as Record<string, unknown>
}

const resolveProviderName = (_providerId: string, data: Record<string, unknown>): string =>
  readString(data.nome) ||
  readString(data.name) ||
  readString(data.fullName)

const resolvePixKey = (data: Record<string, unknown>): string => {
  return readString(data.pixKey)
}

const resolvePixKeyType = (data: Record<string, unknown>): string => {
  return readString(data.pixKeyType)
}

const createProviderEntry = (
  providerId: string,
  data: Record<string, unknown>,
  fallback?: { nome?: string; phone?: string; email?: string }
): ProviderBilling => ({
  id: providerId,
  uid: readString(data.uid) || providerId,
  nome: resolveProviderName(providerId, data) || fallback?.nome || '',
  phone: readString(data.phone) || readString(data.telefone) || fallback?.phone || '',
  email: readString(data.email) || fallback?.email || '',
  pixKey: resolvePixKey(data),
  pixKeyType: resolvePixKeyType(data),
  totalEarnings: 0,
  totalEarningsCents: 0,
  totalJobs: 0,
  eligibleOrdersCount: 0,
  pendingOrdersCount: 0,
  isActive: typeof data.isActive === 'boolean' ? data.isActive : undefined,
  isVerified: typeof data.isVerified === 'boolean' ? data.isVerified : undefined,
  verificationStatus: readString(data.verificationStatus) || undefined,
  updatedAt: toIsoDate(data.updatedAt, '') || undefined,
})

/**
 * GET /api/financial/providers
 * Calcula saldo de pagamento dos prestadores a partir de orders.
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    if (!adminApp) {
      return NextResponse.json(
        {
          success: false,
          error: 'Firebase Admin SDK não configurado',
          providers: [],
        },
        { status: 500 }
      )
    }

    const db = adminApp.firestore()

    const [providersSnapshot, ordersSnapshot] = await Promise.all([
      db.collection('providers').get(),
      db.collection('orders').get(),
    ])

    const providerMap = new Map<string, ProviderBilling>()
    const providerLookup = new Map<string, string>()

    providersSnapshot.forEach((providerDoc) => {
      const providerData = providerDoc.data() as Record<string, unknown>
      const provider = createProviderEntry(providerDoc.id, providerData)
      providerMap.set(providerDoc.id, provider)
      providerLookup.set(providerDoc.id, providerDoc.id)

      const providerDataId = readString(providerData.id)
      if (providerDataId) {
        providerLookup.set(providerDataId, providerDoc.id)
      }

      const providerDataProviderId = readString(providerData.providerId)
      if (providerDataProviderId) {
        providerLookup.set(providerDataProviderId, providerDoc.id)
      }

      const providerDataProviderUid = readString(providerData.providerUid)
      if (providerDataProviderUid) {
        providerLookup.set(providerDataProviderUid, providerDoc.id)
      }

      const providerVerificationCode =
        readString(providerData.providerVerificationCode) ||
        readString(providerData.verificationCode) ||
        readString(providerData.verification_code)
      if (providerVerificationCode) {
        providerLookup.set(providerVerificationCode, providerDoc.id)
      }

      if (provider.uid) {
        providerLookup.set(provider.uid, providerDoc.id)
      }
    })

    ordersSnapshot.forEach((orderDoc) => {
      const orderData = orderDoc.data() as Record<string, unknown>
      const payout = buildOrderPayoutSnapshot(orderDoc.id, orderData)
      if (!payout.eligible) {
        return
      }

      const providerKey = providerLookup.get(payout.providerId) || payout.providerId
      let providerEntry = providerMap.get(providerKey)
      if (!providerEntry) {
        const prestador = getNestedRecord(orderData.prestador)
        providerEntry = createProviderEntry(
          providerKey,
          {},
          {
            nome: readString(orderData.providerName) || readString(prestador.nome),
            phone: readString(prestador.telefone),
            email: readString(orderData.providerEmail),
          }
        )
        providerMap.set(providerKey, providerEntry)
      }

      providerLookup.set(payout.providerId, providerKey)
      if (providerEntry.uid) {
        providerLookup.set(providerEntry.uid, providerKey)
      }

      providerEntry.totalJobs = (providerEntry.totalJobs ?? 0) + 1
      providerEntry.eligibleOrdersCount += 1

      if (payout.remainingCents > 0) {
        providerEntry.pendingOrdersCount += 1
        providerEntry.totalEarningsCents += payout.remainingCents
      }
    })

    const providers = Array.from(providerMap.values())
      .filter((provider) => provider.isActive !== false || provider.eligibleOrdersCount > 0)
      .map((provider) => ({
        ...provider,
        totalEarnings: centsToAmount(provider.totalEarningsCents),
      }))
      .sort((a, b) => b.totalEarningsCents - a.totalEarningsCents)

    const totalEarningsCents = providers.reduce(
      (sum, provider) => sum + provider.totalEarningsCents,
      0
    )

    const warning =
      providers.length === 0
        ? 'Nenhum prestador real encontrado para faturamento.'
        : totalEarningsCents <= 0
          ? 'Nenhum saldo pendente real encontrado para prestadores.'
          : undefined

    return NextResponse.json({
      success: true,
      providers,
      totalEarnings: centsToAmount(totalEarningsCents),
      totalEarningsCents,
      count: providers.length,
      warning,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar prestadores:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao buscar prestadores',
        providers: [],
      },
      { status: 500 }
    )
  }
}
