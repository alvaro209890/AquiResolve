"use client"

import { useState, useEffect } from 'react'
import { UsersService, type UserFilters, type UserStats } from '@/lib/services/users-service'
import type { UserData } from '@/lib/services/firestore-analytics'

export function useUsers(filters?: UserFilters) {
  const [users, setUsers] = useState<UserData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchUsers = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await UsersService.getUsers(filters)
      setUsers(data)
    } catch (err) {
      console.error('Erro ao buscar usuários:', err)
      setError('Erro ao carregar usuários')
    } finally {
      setLoading(false)
    }
  }

  const createUser = async (userData: Partial<UserData>) => {
    try {
      setLoading(true)
      const newUser = await UsersService.createUser(userData)
      setUsers(prev => [newUser, ...prev])
      return newUser
    } catch (err) {
      console.error('Erro ao criar usuário:', err)
      setError('Erro ao criar usuário')
      throw err
    } finally {
      setLoading(false)
    }
  }

  const updateUser = async (userId: string, userData: Partial<UserData>) => {
    try {
      setLoading(true)
      await UsersService.updateUser(userId, userData)
      setUsers(prev => prev.map(user => 
        user.id === userId ? { ...user, ...userData } : user
      ))
    } catch (err) {
      console.error('Erro ao atualizar usuário:', err)
      setError('Erro ao atualizar usuário')
      throw err
    } finally {
      setLoading(false)
    }
  }

  const deleteUser = async (userId: string) => {
    try {
      setLoading(true)
      await UsersService.deleteUser(userId)
      setUsers(prev => prev.filter(user => user.id !== userId))
    } catch (err) {
      console.error('Erro ao deletar usuário:', err)
      setError('Erro ao deletar usuário')
      throw err
    } finally {
      setLoading(false)
    }
  }

  const toggleUserStatus = async (userId: string, isActive: boolean) => {
    try {
      await UsersService.toggleUserStatus(userId, isActive)
      setUsers(prev => prev.map(user => 
        user.id === userId ? { ...user, isActive } : user
      ))
    } catch (err) {
      console.error('Erro ao alterar status do usuário:', err)
      setError('Erro ao alterar status do usuário')
      throw err
    }
  }

  const blockUser = async (userId: string, reason?: string) => {
    try {
      // Usa Admin SDK via API route (não depende de custom claims)
      const res = await fetch(`/api/users/${userId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ blocked: true, isActive: false, blockedReason: reason ?? 'Bloqueado pelo administrador' }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error)
      setUsers(prev => prev.map(user =>
        user.id === userId ? { ...user, isActive: false, blocked: true } : user
      ))
    } catch (err) {
      console.error('Erro ao bloquear usuário:', err)
      setError('Erro ao bloquear usuário')
      throw err
    }
  }

  const unblockUser = async (userId: string) => {
    try {
      const res = await fetch(`/api/users/${userId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ blocked: false, isActive: true }),
      })
      const data = await res.json()
      if (!data.success) throw new Error(data.error)
      setUsers(prev => prev.map(user =>
        user.id === userId ? { ...user, isActive: true, blocked: false } : user
      ))
    } catch (err) {
      console.error('Erro ao desbloquear usuário:', err)
      setError('Erro ao desbloquear usuário')
      throw err
    }
  }

  useEffect(() => {
    fetchUsers()
  }, [JSON.stringify(filters)])

  return {
    users,
    loading,
    error,
    refetch: fetchUsers,
    createUser,
    updateUser,
    deleteUser,
    toggleUserStatus,
    blockUser,
    unblockUser
  }
}

export function useUserStats(filters?: UserFilters) {
  const [stats, setStats] = useState<UserStats | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchStats = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await UsersService.getUserStats(filters)
      setStats(data)
    } catch (err) {
      console.error('Erro ao buscar estatísticas dos usuários:', err)
      setError('Erro ao carregar estatísticas')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchStats()
  }, [JSON.stringify(filters)])

  return {
    stats,
    loading,
    error,
    refetch: fetchStats
  }
}

export function useRecentUsers(limitCount: number = 10) {
  const [users, setUsers] = useState<UserData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchRecentUsers = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await UsersService.getRecentUsers(limitCount)
      setUsers(data)
    } catch (err) {
      console.error('Erro ao buscar usuários recentes:', err)
      setError('Erro ao carregar usuários recentes')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchRecentUsers()
  }, [limitCount])

  return {
    users,
    loading,
    error,
    refetch: fetchRecentUsers
  }
}

export function useActiveUsers() {
  const [users, setUsers] = useState<UserData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchActiveUsers = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await UsersService.getActiveUsers()
      setUsers(data)
    } catch (err) {
      console.error('Erro ao buscar usuários ativos:', err)
      setError('Erro ao carregar usuários ativos')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchActiveUsers()
  }, [])

  return {
    users,
    loading,
    error,
    refetch: fetchActiveUsers
  }
}

export function useUsersWithRecentLogin() {
  const [users, setUsers] = useState<UserData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchUsersWithRecentLogin = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await UsersService.getUsersWithRecentLogin()
      setUsers(data)
    } catch (err) {
      console.error('Erro ao buscar usuários com login recente:', err)
      setError('Erro ao carregar usuários com login recente')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchUsersWithRecentLogin()
  }, [])

  return {
    users,
    loading,
    error,
    refetch: fetchUsersWithRecentLogin
  }
}

export function useAllClients() {
  const [clients, setClients] = useState<UserData[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchAllClients = async () => {
    try {
      setLoading(true)
      setError(null)
      const data = await UsersService.getAllClients()
      setClients(data)
    } catch (err) {
      console.error('❌ [useAllClients] Erro ao buscar clientes:', err)
      setError('Erro ao carregar clientes')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    fetchAllClients()
  }, [])

  return {
    clients,
    loading,
    error,
    refetch: fetchAllClients
  }
}