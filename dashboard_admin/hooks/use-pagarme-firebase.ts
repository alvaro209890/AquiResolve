"use client"

/**
 * HOOKS HÍBRIDOS: PAGAR.ME + FIREBASE
 * Usa dados do Firestore com fallback para API do Pagar.me
 */

import { useState, useEffect, useCallback } from 'react'
import { PagarmeFirebaseSync } from '@/lib/services/pagarme-firebase-sync'
import { adminFetch } from '@/lib/admin-api'
import {
  PagarmeOrder,
  PagarmeCharge,
  PagarmeCustomer,
} from '@/types/pagarme'

// ==========================================
// HOOK: usePagarmeOrdersFirebase
// ==========================================
export function usePagarmeOrdersFirebase(options?: {
  status?: string
  useFirestore?: boolean
}) {
  const [orders, setOrders] = useState<PagarmeOrder[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchOrders = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // Tentar buscar do Firestore primeiro
      if (options?.useFirestore !== false) {
        const firestoreOrders = await PagarmeFirebaseSync.getOrders({
          status: options?.status,
          limit: 100
        })

        if (firestoreOrders.length > 0) {
          setOrders(firestoreOrders as PagarmeOrder[])
          setLoading(false)
          return
        }
      }

      // Fallback para API do Pagar.me
      const params = new URLSearchParams()
      if (options?.status) params.append('status', options.status)

      const response = await adminFetch(`/api/pagarme/orders?${params.toString()}`)
      const data = await response.json()

      if (data.success) {
        setOrders(data.data || [])
      } else {
        setError(data.error || 'Erro ao buscar pedidos')
      }
    } catch (err) {
      console.error('Erro ao buscar pedidos:', err)
      setError('Erro ao buscar pedidos')
    } finally {
      setLoading(false)
    }
  }, [options?.status, options?.useFirestore])

  useEffect(() => {
    fetchOrders()
  }, [fetchOrders])

  return {
    orders,
    loading,
    error,
    refetch: fetchOrders,
  }
}

// ==========================================
// HOOK: usePagarmeChargesFirebase
// ==========================================
export function usePagarmeChargesFirebase(options?: {
  status?: string
  useFirestore?: boolean
  realtime?: boolean
}) {
  const [charges, setCharges] = useState<PagarmeCharge[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchCharges = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)

      // Tentar buscar do Firestore primeiro
      if (options?.useFirestore !== false) {
        const firestoreCharges = await PagarmeFirebaseSync.getCharges({
          status: options?.status,
          limit: 100
        })

        if (firestoreCharges.length > 0) {
          setCharges(firestoreCharges as PagarmeCharge[])
          setLoading(false)
          return
        }
      }

      // Fallback para API do Pagar.me
      const params = new URLSearchParams()
      if (options?.status) params.append('status', options.status)

      const response = await adminFetch(`/api/pagarme/charges?${params.toString()}`)
      const data = await response.json()

      if (data.success) {
        setCharges(data.data || [])
      } else {
        setError(data.error || 'Erro ao buscar cobranças')
      }
    } catch (err) {
      console.error('Erro ao buscar cobranças:', err)
      setError('Erro ao buscar cobranças')
    } finally {
      setLoading(false)
    }
  }, [options?.status, options?.useFirestore])

  useEffect(() => {
    if (options?.realtime && options?.useFirestore !== false) {
      // Usar listener em tempo real do Firestore
      const unsubscribe = PagarmeFirebaseSync.listenToCharges((firestoreCharges) => {
        setCharges(firestoreCharges as PagarmeCharge[])
        setLoading(false)
      })
      return unsubscribe
    } else {
      fetchCharges()
    }
  }, [fetchCharges, options?.realtime, options?.useFirestore])

  return {
    charges,
    loading,
    error,
    refetch: fetchCharges,
  }
}

/**
 * Hook de sincronização manual
 */
export function usePagarmeSync() {
  const [syncing, setSyncing] = useState(false)
  const [lastSync, setLastSync] = useState<any>(null)

  const syncAll = async () => {
    try {
      setSyncing(true)
      const response = await adminFetch('/api/pagarme/sync', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: 'all', limit: 100 })
      })
      const data = await response.json()

      if (data.success) {
        setLastSync(data.synced)
        return { success: true, data: data.synced }
      } else {
        return { success: false, error: data.error }
      }
    } catch (error) {
      console.error('Erro ao sincronizar:', error)
      return { success: false, error: 'Erro ao sincronizar' }
    } finally {
      setSyncing(false)
    }
  }

  const getSyncStatus = async () => {
    try {
      const response = await adminFetch('/api/pagarme/sync')
      const data = await response.json()
      if (data.success) {
        setLastSync(data.data)
      }
    } catch (error) {
      console.error('Erro ao buscar status:', error)
    }
  }

  useEffect(() => {
    getSyncStatus()
  }, [])

  return {
    syncAll,
    syncing,
    lastSync,
    getSyncStatus,
  }
}
