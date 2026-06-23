"use client"

import { useCallback, useEffect, useState } from 'react'
import { adminFetch } from '@/lib/admin-api'

export interface PagarmeAnalyticsData {
  totalRevenue: number
  totalTransactions: number
  successRate: number
  conversionRate: number
  paymentMethods: {
    credit_card: number
    debit_card: number
    pix: number
    boleto: number
  }
  recentCharges: any[]
  loading: boolean
  error: string | null
  warning: string | null
}

const emptyPaymentMethods = {
  credit_card: 0,
  debit_card: 0,
  pix: 0,
  boleto: 0,
}

export function usePagarmeAnalytics() {
  const [data, setData] = useState<PagarmeAnalyticsData>({
    totalRevenue: 0,
    totalTransactions: 0,
    successRate: 0,
    conversionRate: 0,
    paymentMethods: emptyPaymentMethods,
    recentCharges: [],
    loading: true,
    error: null,
    warning: null,
  })

  const fetchAnalytics = useCallback(async () => {
    try {
      setData((previous) => ({
        ...previous,
        loading: true,
        error: null,
        warning: null,
      }))

      const response = await adminFetch('/api/pagarme/analytics', {
        method: 'GET',
        headers: {
          'Content-Type': 'application/json',
        },
      })

      if (!response.ok) {
        throw new Error('Erro ao buscar dados do Pagar.me')
      }

      const analytics = await response.json()

      setData({
        totalRevenue: analytics.totalRevenue || 0,
        totalTransactions: analytics.totalTransactions || 0,
        successRate: analytics.successRate || 0,
        conversionRate: analytics.conversionRate || 0,
        paymentMethods: analytics.paymentMethods || emptyPaymentMethods,
        recentCharges: analytics.recentCharges || [],
        loading: false,
        error: null,
        warning: analytics.warning || null,
      })
    } catch (error) {
      console.error('Erro ao buscar analytics:', error)
      setData((previous) => ({
        ...previous,
        loading: false,
        error: 'Erro ao carregar dados financeiros',
        warning: null,
      }))
    }
  }, [])

  useEffect(() => {
    fetchAnalytics()
  }, [fetchAnalytics])

  return { ...data, refetch: fetchAnalytics }
}
