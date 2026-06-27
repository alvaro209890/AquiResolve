'use client'

import { useCallback, useEffect, useState } from 'react'
import { adminFetch } from '@/lib/admin-api'

export interface FinancialAnalyticsData {
  totalRevenue: number
  totalTransactions: number
  averageTicket: number
  successRate: number
  conversionRate: number
  totalCustomers: number
  totalProviders: number
  platformCommission: number
  cashbackDistributed: number
  pendingProviderPayouts: number
  dailyRevenue: { date: string; receita: number; transacoes: number }[]
  paymentMethodTrend: { date: string; credit_card: number; debit_card: number; pix: number; boleto: number }[]
  paymentMethods: { credit_card: number; debit_card: number; pix: number; boleto: number }
  statusBreakdown: { paid: number; pending: number; failed: number; canceled: number }
  topServices: { name: string; revenue: number; count: number }[]
  recentTransactions: { id: string; status: string; amount: number; method: string; date: string; service: string }[]
  period: { start: string; end: string }
  source: string
}

const defaultAnalytics: FinancialAnalyticsData = {
  totalRevenue: 0,
  totalTransactions: 0,
  averageTicket: 0,
  successRate: 0,
  conversionRate: 0,
  totalCustomers: 0,
  totalProviders: 0,
  platformCommission: 0,
  cashbackDistributed: 0,
  pendingProviderPayouts: 0,
  dailyRevenue: [],
  paymentMethodTrend: [],
  paymentMethods: { credit_card: 0, debit_card: 0, pix: 0, boleto: 0 },
  statusBreakdown: { paid: 0, pending: 0, failed: 0, canceled: 0 },
  topServices: [],
  recentTransactions: [],
  period: { start: '', end: '' },
  source: '',
}

export function useFinancialAnalytics(period: string = '30d') {
  const [data, setData] = useState<FinancialAnalyticsData>(defaultAnalytics)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const fetchData = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setWarning(null)

      const response = await adminFetch(
        `/api/financial/analytics?period=${period}`,
        { headers: { 'Content-Type': 'application/json' } }
      )

      if (!response.ok) {
        throw new Error(`Erro HTTP ${response.status}`)
      }

      const json = await response.json()

      if (json.success && json.data) {
        setData(json.data)
        setWarning(json.warning || null)
      } else {
        throw new Error(json.error || 'Erro ao carregar dados')
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao carregar analytics')
    } finally {
      setLoading(false)
    }
  }, [period])

  useEffect(() => {
    fetchData()
  }, [fetchData])

  return { ...data, loading, error, warning, refetch: fetchData }
}
