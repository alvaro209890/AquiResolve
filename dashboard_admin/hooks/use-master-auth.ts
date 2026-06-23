"use client"

import React, { useState, useEffect, createContext, useContext } from "react"
import { AdminMasterService } from "@/lib/services/admin-master-service"
import { clientSessionEncryption } from "@/lib/client-session-encryption"
import type { AdminPermissions } from "@/lib/admin-permissions"
import { normalizeAdminPermissions } from "@/lib/admin-permissions"

export interface MasterUser {
  id: string
  email: string
  nome: string
  permissoes: AdminPermissions
}

export interface AdminMaster {
  id: string
  email: string
  senhaHash: string
  nome: string
  permissoes: AdminPermissions
}

interface MasterAuthContextType {
  isMasterAuthenticated: boolean
  masterUser: MasterUser | null
  masterLogin: (email: string, password: string) => Promise<void>
  masterLogout: () => void
  usuarios: MasterUser[]
  loading: boolean
  addUsuario: (usuario: Omit<MasterUser, 'id'>) => Promise<void>
  updateUsuario: (id: string, permissoes: MasterUser['permissoes']) => Promise<void>
  deleteUsuario: (id: string) => Promise<void>
  refreshUsuarios: () => Promise<void>
  changeUserPassword: (userId: string, newPassword: string) => Promise<void>
}

const MasterAuthContext = createContext<MasterAuthContextType>({
  isMasterAuthenticated: false,
  masterUser: null,
  masterLogin: async () => {},
  masterLogout: () => {},
  usuarios: [],
  loading: true,
  addUsuario: async () => {},
  updateUsuario: async () => {},
  deleteUsuario: async () => {},
  refreshUsuarios: async () => {},
  changeUserPassword: async () => {}
})

export function MasterAuthProvider({ children }: { children: React.ReactNode }) {
  const [isMasterAuthenticated, setIsMasterAuthenticated] = useState(false)
  const [masterUser, setMasterUser] = useState<MasterUser | null>(null)
  const [usuarios, setUsuarios] = useState<MasterUser[]>([])
  const [loading, setLoading] = useState(true)


  // Sempre exigir login ao acessar /master. Não restauramos sessão; uso de sessionStorage
  // criptografado apenas durante a navegação na mesma aba (evita re-login a cada clique).
  useEffect(() => {
    let cancelled = false
    const timeout = window.setTimeout(() => {
      if (cancelled) return
      setLoading(false)
    }, 10000) // segurança: nunca ficar mais de 10s em "Verificando autenticação..."

    const checkMasterAuth = async () => {
      try {
        setLoading(true)
        const payload = await clientSessionEncryption.load()
        if (!payload?.userId || !payload?.email || !payload?.permissoes) {
          setLoading(false)
          return
        }
        const sessionResponse = await fetch('/api/auth/master-session', { cache: 'no-store' })
        const sessionData = await sessionResponse.json().catch(() => ({}))
        if (!sessionResponse.ok || !sessionData?.success || !sessionData?.user) {
          throw new Error('Sessão Master inválida ou expirada')
        }
        if (cancelled) return
        const verifiedUser = sessionData.user as MasterUser
        const user: MasterUser = {
          id: verifiedUser.id,
          email: verifiedUser.email,
          nome: verifiedUser.nome ?? 'Master',
          permissoes: normalizeAdminPermissions(verifiedUser.permissoes, { master: true }),
        }
        setMasterUser(user)
        setIsMasterAuthenticated(true)
        await loadUsuarios(payload.userId)
      } catch (error) {
        console.error('Erro ao verificar autenticação master:', error)
        clientSessionEncryption.clear()
        setMasterUser(null)
        setIsMasterAuthenticated(false)
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    checkMasterAuth()
    return () => {
      cancelled = true
      window.clearTimeout(timeout)
    }
  }, [])

  // Permitir que outras partes da UI solicitem atualização da lista de usuários
  useEffect(() => {
    const handler = () => {
      if (masterUser) {
        loadUsuarios(masterUser.id)
      }
    }
    if (typeof window !== 'undefined') {
      window.addEventListener('refresh-master-usuarios', handler as EventListener)
    }
    return () => {
      if (typeof window !== 'undefined') {
        window.removeEventListener('refresh-master-usuarios', handler as EventListener)
      }
    }
  }, [masterUser])

  const masterLogin = async (email: string, password: string) => {
    try {
      setLoading(true)

      const res = await fetch('/api/auth/master-login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      })
      const data = await res.json().catch(() => ({}))

      if (!res.ok) {
        const msg = data?.error ?? 'Credenciais inválidas'
        throw new Error(msg)
      }

      const { user: adminMaster } = data as { user: MasterUser }
      if (!adminMaster?.id) throw new Error('Resposta inválida')

      setMasterUser(adminMaster)
      setIsMasterAuthenticated(true)

      await clientSessionEncryption.save({
        userId: adminMaster.id,
        email: adminMaster.email,
        nome: adminMaster.nome,
        permissoes: adminMaster.permissoes,
        loggedAt: Date.now()
      })

      await loadUsuarios(adminMaster.id)
    } catch (error) {
      console.error('Erro no login master:', error)
      setMasterUser(null)
      setIsMasterAuthenticated(false)
      throw error
    } finally {
      setLoading(false)
    }
  }

  const masterLogout = () => {
    void fetch('/api/auth/master-logout', { method: 'POST' }).catch(() => undefined)
    setMasterUser(null)
    setIsMasterAuthenticated(false)
    setUsuarios([])
    clientSessionEncryption.clear()
  }

  const loadUsuarios = async (adminId: string) => {
    try {
      const usuariosList = await AdminMasterService.getUsuarios(adminId)
      setUsuarios(usuariosList)
    } catch (error) {
      console.error('Erro ao carregar usuários:', error)
    }
  }

  const addUsuario = async (usuario: Omit<MasterUser, 'id'>) => {
    try {
      if (!masterUser) throw new Error('Não autenticado como master')
      
      await AdminMasterService.addUsuario(masterUser.id, usuario)
      await refreshUsuarios()
    } catch (error) {
      console.error('Erro ao adicionar usuário:', error)
      throw error
    }
  }

  const updateUsuario = async (id: string, permissoes: MasterUser['permissoes']) => {
    try {
      if (!masterUser) throw new Error('Não autenticado como master')
      
      // Permite atualizar apenas o objeto de permissões
      await AdminMasterService.updateUsuario(masterUser.id, id, permissoes)
      await refreshUsuarios()
    } catch (error) {
      console.error('Erro ao atualizar usuário:', error)
      throw error
    }
  }

  const deleteUsuario = async (id: string) => {
    try {
      if (!masterUser) throw new Error('Não autenticado como master')
      
      await AdminMasterService.deleteUsuario(masterUser.id, id)
      await refreshUsuarios()
    } catch (error) {
      console.error('Erro ao deletar usuário:', error)
      throw error
    }
  }

  const refreshUsuarios = async () => {
    if (masterUser) {
      await loadUsuarios(masterUser.id)
    }
  }

  const changeUserPassword = async (userId: string, newPassword: string) => {
    try {
      if (!masterUser) throw new Error('Não autenticado como master')
      
      const response = await fetch(`/api/adminmaster/users/${userId}/password`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPassword })
      })

      const data = await response.json()
      
      if (!response.ok) {
        throw new Error(data.error || 'Falha ao alterar senha')
      }

      return data
    } catch (error) {
      console.error('Erro ao alterar senha:', error)
      throw error
    }
  }

  const contextValue = {
    isMasterAuthenticated,
    masterUser,
    masterLogin,
    masterLogout,
    usuarios,
    loading,
    addUsuario,
    updateUsuario,
    deleteUsuario,
    refreshUsuarios,
    changeUserPassword
  }

  return React.createElement(
    MasterAuthContext.Provider,
    { value: contextValue },
    children
  )
}

export const useMasterAuth = () => useContext(MasterAuthContext)
