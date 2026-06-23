"use client"

import { useState, useEffect, useCallback } from 'react'
import {
  PagarmeOrder,
  PagarmeCustomer,
  PagarmeCharge,
  PagarmeSubscription,
  PagarmeBalance,
  PagarmeAnalytics,
} from '@/types/pagarme'
import { adminFetch } from '@/lib/admin-api'

interface ApiEnvelope<T> {
  success: boolean
  data?: T
  error?: string
  errors?: Array<{ message?: string }>
  warning?: string
}

export function usePagarmeOrders(options?: {
  status?: string
  customer_id?: string
  autoRefresh?: boolean
}) {
  const [orders, setOrders] = useState<PagarmeOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const fetchOrders = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setWarning(null)

      const params = new URLSearchParams()
      if (options?.status) params.append('status', options.status)
      if (options?.customer_id) params.append('customer_id', options.customer_id)

      const response = await adminFetch(`/api/pagarme/orders?${params.toString()}`)
      const data = (await response.json()) as ApiEnvelope<PagarmeOrder[]>

      if (data.success) {
        setOrders(data.data || [])
        setWarning(data.warning || null)
      } else {
        setWarning(data.warning || null)
        setError(data.error || data.errors?.[0]?.message || 'Erro ao buscar pedidos')
      }
    } catch (err) {
      console.error('Erro ao buscar pedidos:', err)
      setError('Erro ao buscar pedidos')
      setWarning(null)
    } finally {
      setLoading(false)
    }
  }, [options?.status, options?.customer_id])

  useEffect(() => {
    void fetchOrders()

    if (options?.autoRefresh) {
      const interval = setInterval(() => {
        void fetchOrders()
      }, 30000)
      return () => clearInterval(interval)
    }

    return undefined
  }, [fetchOrders, options?.autoRefresh])

  const createOrder = async (orderData: unknown) => {
    try {
      const response = await adminFetch('/api/pagarme/orders', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(orderData),
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeOrder>

      if (data.success) {
        await fetchOrders()
        return { success: true, data: data.data, warning: data.warning }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao criar pedido' }
    } catch (err) {
      console.error('Erro ao criar pedido:', err)
      return { success: false, error: 'Erro ao criar pedido' }
    }
  }

  const cancelOrder = async (orderId: string) => {
    try {
      const response = await adminFetch(`/api/pagarme/orders/${orderId}`, {
        method: 'DELETE',
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeOrder>

      if (data.success) {
        await fetchOrders()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao cancelar pedido' }
    } catch (err) {
      console.error('Erro ao cancelar pedido:', err)
      return { success: false, error: 'Erro ao cancelar pedido' }
    }
  }

  return {
    orders,
    loading,
    error,
    warning,
    refetch: fetchOrders,
    createOrder,
    cancelOrder,
  }
}

export function usePagarmeCustomers() {
  const [customers, setCustomers] = useState<PagarmeCustomer[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchCustomers = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const response = await adminFetch('/api/pagarme/customers')
      const data = (await response.json()) as ApiEnvelope<PagarmeCustomer[]>

      if (data.success) {
        setCustomers(data.data || [])
      } else {
        setError(data.error || data.errors?.[0]?.message || 'Erro ao buscar clientes')
      }
    } catch (err) {
      console.error('Erro ao buscar clientes:', err)
      setError('Erro ao buscar clientes')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchCustomers()
  }, [fetchCustomers])

  const createCustomer = async (customerData: Partial<PagarmeCustomer>) => {
    try {
      const response = await adminFetch('/api/pagarme/customers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(customerData),
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeCustomer>

      if (data.success) {
        await fetchCustomers()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao criar cliente' }
    } catch (err) {
      console.error('Erro ao criar cliente:', err)
      return { success: false, error: 'Erro ao criar cliente' }
    }
  }

  const updateCustomer = async (customerId: string, customerData: Partial<PagarmeCustomer>) => {
    try {
      const response = await adminFetch(`/api/pagarme/customers/${customerId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(customerData),
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeCustomer>

      if (data.success) {
        await fetchCustomers()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao atualizar cliente' }
    } catch (err) {
      console.error('Erro ao atualizar cliente:', err)
      return { success: false, error: 'Erro ao atualizar cliente' }
    }
  }

  return {
    customers,
    loading,
    error,
    refetch: fetchCustomers,
    createCustomer,
    updateCustomer,
  }
}

export function usePagarmeCharges(options?: {
  status?: string
  customer_id?: string
  autoRefresh?: boolean
}) {
  const [charges, setCharges] = useState<PagarmeCharge[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const fetchCharges = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setWarning(null)

      const params = new URLSearchParams()
      if (options?.status) params.append('status', options.status)
      if (options?.customer_id) params.append('customer_id', options.customer_id)

      const response = await adminFetch(`/api/pagarme/charges?${params.toString()}`)
      const data = (await response.json()) as ApiEnvelope<PagarmeCharge[]>

      if (data.success) {
        setCharges(data.data || [])
        setWarning(data.warning || null)
      } else {
        setWarning(data.warning || null)
        setError(data.error || data.errors?.[0]?.message || 'Erro ao buscar cobrancas')
      }
    } catch (err) {
      console.error('Erro ao buscar cobrancas:', err)
      setError('Erro ao buscar cobrancas')
      setWarning(null)
    } finally {
      setLoading(false)
    }
  }, [options?.status, options?.customer_id])

  useEffect(() => {
    void fetchCharges()

    if (options?.autoRefresh) {
      const interval = setInterval(() => {
        void fetchCharges()
      }, 30000)
      return () => clearInterval(interval)
    }

    return undefined
  }, [fetchCharges, options?.autoRefresh])

  const refundCharge = async (chargeId: string, amount?: number) => {
    try {
      const response = await adminFetch(`/api/pagarme/charges/${chargeId}/refund`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ amount }),
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeCharge>

      if (data.success) {
        await fetchCharges()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao reembolsar cobranca' }
    } catch (err) {
      console.error('Erro ao reembolsar cobranca:', err)
      return { success: false, error: 'Erro ao reembolsar cobranca' }
    }
  }

  const cancelCharge = async (chargeId: string) => {
    try {
      const response = await adminFetch(`/api/pagarme/charges/${chargeId}`, {
        method: 'DELETE',
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeCharge>

      if (data.success) {
        await fetchCharges()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao cancelar cobranca' }
    } catch (err) {
      console.error('Erro ao cancelar cobranca:', err)
      return { success: false, error: 'Erro ao cancelar cobranca' }
    }
  }

  return {
    charges,
    loading,
    error,
    warning,
    refetch: fetchCharges,
    refundCharge,
    cancelCharge,
  }
}

export function usePagarmeSubscriptions(options?: {
  status?: string
  customer_id?: string
  autoRefresh?: boolean
}) {
  const [subscriptions, setSubscriptions] = useState<PagarmeSubscription[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchSubscriptions = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      const params = new URLSearchParams()
      if (options?.status) params.append('status', options.status)
      if (options?.customer_id) params.append('customer_id', options.customer_id)

      const response = await adminFetch(`/api/pagarme/subscriptions?${params.toString()}`)
      const data = (await response.json()) as ApiEnvelope<PagarmeSubscription[]>

      if (data.success) {
        setSubscriptions(data.data || [])
      } else {
        setError(data.error || data.errors?.[0]?.message || 'Erro ao buscar assinaturas')
      }
    } catch (err) {
      console.error('Erro ao buscar assinaturas:', err)
      setError('Erro ao buscar assinaturas')
    } finally {
      setLoading(false)
    }
  }, [options?.status, options?.customer_id])

  useEffect(() => {
    void fetchSubscriptions()

    if (options?.autoRefresh) {
      const interval = setInterval(() => {
        void fetchSubscriptions()
      }, 60000)
      return () => clearInterval(interval)
    }

    return undefined
  }, [fetchSubscriptions, options?.autoRefresh])

  const createSubscription = async (subscriptionData: unknown) => {
    try {
      const response = await adminFetch('/api/pagarme/subscriptions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(subscriptionData),
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeSubscription>

      if (data.success) {
        await fetchSubscriptions()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao criar assinatura' }
    } catch (err) {
      console.error('Erro ao criar assinatura:', err)
      return { success: false, error: 'Erro ao criar assinatura' }
    }
  }

  const cancelSubscription = async (subscriptionId: string) => {
    try {
      const response = await adminFetch(`/api/pagarme/subscriptions/${subscriptionId}`, {
        method: 'DELETE',
      })
      const data = (await response.json()) as ApiEnvelope<PagarmeSubscription>

      if (data.success) {
        await fetchSubscriptions()
        return { success: true, data: data.data }
      }

      return { success: false, error: data.error || data.errors?.[0]?.message || 'Erro ao cancelar assinatura' }
    } catch (err) {
      console.error('Erro ao cancelar assinatura:', err)
      return { success: false, error: 'Erro ao cancelar assinatura' }
    }
  }

  return {
    subscriptions,
    loading,
    error,
    refetch: fetchSubscriptions,
    createSubscription,
    cancelSubscription,
  }
}

export function usePagarmeBalance(autoRefresh?: boolean) {
  const [balance, setBalance] = useState<PagarmeBalance | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const fetchBalance = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setWarning(null)

      const response = await adminFetch('/api/pagarme/balance')
      const data = (await response.json()) as ApiEnvelope<PagarmeBalance>

      if (data.success) {
        setBalance(data.data || null)
        setWarning(data.warning || null)
      } else {
        setWarning(data.warning || null)
        setError(data.error || data.errors?.[0]?.message || 'Erro ao buscar saldo')
      }
    } catch (err) {
      console.error('Erro ao buscar saldo:', err)
      setError('Erro ao buscar saldo')
      setWarning(null)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchBalance()

    if (autoRefresh) {
      const interval = setInterval(() => {
        void fetchBalance()
      }, 60000)
      return () => clearInterval(interval)
    }

    return undefined
  }, [fetchBalance, autoRefresh])

  return {
    balance,
    loading,
    error,
    warning,
    refetch: fetchBalance,
  }
}

export function usePagarmeAnalytics(startDate: string, endDate: string) {
  const [analytics, setAnalytics] = useState<PagarmeAnalytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [warning, setWarning] = useState<string | null>(null)

  const fetchAnalytics = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      setWarning(null)

      const params = new URLSearchParams({
        start_date: startDate,
        end_date: endDate,
      })

      const response = await adminFetch(`/api/pagarme/analytics?${params.toString()}`)
      const data = (await response.json()) as ApiEnvelope<PagarmeAnalytics>

      if (data.success) {
        setAnalytics(data.data || null)
        setWarning(data.warning || null)
      } else {
        setWarning(data.warning || null)
        setError(data.error || data.errors?.[0]?.message || 'Erro ao buscar analytics')
      }
    } catch (err) {
      console.error('Erro ao buscar analytics:', err)
      setError('Erro ao buscar analytics')
      setWarning(null)
    } finally {
      setLoading(false)
    }
  }, [startDate, endDate])

  useEffect(() => {
    if (startDate && endDate) {
      void fetchAnalytics()
    }
  }, [fetchAnalytics, startDate, endDate])

  return {
    analytics,
    loading,
    error,
    warning,
    refetch: fetchAnalytics,
  }
}
