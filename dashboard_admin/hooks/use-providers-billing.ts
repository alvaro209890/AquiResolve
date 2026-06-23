import { useCallback, useEffect, useState } from 'react'
import { adminFetch } from '@/lib/admin-api'

export interface ProviderBilling {
  id: string
  uid: string
  nome?: string
  phone?: string
  email?: string
  pixKey?: string
  pixKeyType?: string
  totalEarnings: number
  totalEarningsCents?: number
  totalJobs?: number
  eligibleOrdersCount?: number
  pendingOrdersCount?: number
  isActive?: boolean
  isVerified?: boolean
  verificationStatus?: string
  updatedAt?: string
}

export interface UseProvidersBillingReturn {
  providers: ProviderBilling[]
  loading: boolean
  error: string | null
  warning: string | null
  refetch: () => Promise<void>
  totalEarnings: number
}

interface ProvidersApiResponse {
  success: boolean
  error?: string
  warning?: string
  providers?: ProviderBilling[]
  totalEarnings?: number
}

export function useProvidersBilling(options?: {
  autoRefresh?: boolean
  refreshInterval?: number
}): UseProvidersBillingReturn {
  const [providers, setProviders] = useState<ProviderBilling[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)
  const [totalEarnings, setTotalEarnings] = useState(0)

  const fetchProviders = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setWarning(null)

      const response = await adminFetch('/api/financial/providers', {
        method: 'GET',
        cache: 'no-store',
      })

      const data = (await response.json()) as ProvidersApiResponse
      if (!response.ok || !data.success) {
        throw new Error(data.error || 'Erro ao carregar dados de pagamentos')
      }

      const providersList = Array.isArray(data.providers) ? data.providers : []
      setProviders(providersList)
      setWarning(data.warning || null)
      setTotalEarnings(
        typeof data.totalEarnings === 'number'
          ? data.totalEarnings
          : providersList.reduce((sum, provider) => sum + provider.totalEarnings, 0)
      )
    } catch (err) {
      const errorMessage = err instanceof Error ? err.message : 'Erro ao carregar prestadores'
      setError(errorMessage)
      setWarning(null)
      setProviders([])
      setTotalEarnings(0)
      console.error('Erro ao buscar prestadores:', err)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchProviders()

    if (options?.autoRefresh && options?.refreshInterval) {
      const interval = setInterval(() => {
        void fetchProviders()
      }, options.refreshInterval)
      return () => clearInterval(interval)
    }

    return undefined
  }, [fetchProviders, options?.autoRefresh, options?.refreshInterval])

  return {
    providers,
    loading,
    error,
    warning,
    refetch: fetchProviders,
    totalEarnings,
  }
}
