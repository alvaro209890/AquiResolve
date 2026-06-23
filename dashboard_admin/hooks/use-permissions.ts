"use client"

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react"
import { useAuth } from "@/components/auth-provider"
import {
  AdminPermission,
  AdminPermissions,
  firstAllowedPath,
  hasAdminPermission,
  hasAnyAdminPermission,
  normalizeAdminPermissions,
} from "@/lib/admin-permissions"
import { adminFetch } from "@/lib/admin-api"

export type UserPermissions = AdminPermissions

export interface AdminProfile {
  uid: string
  email: string
  name: string
  isMaster: boolean
}

interface PermissionsContextType {
  permissions: AdminPermissions | null
  profile: AdminProfile | null
  loading: boolean
  error: string | null
  hasPermission: (permission: AdminPermission) => boolean
  hasAnyPermission: (permissions: readonly AdminPermission[]) => boolean
  canAccess: (module: string) => boolean
  firstAllowedPath: string | null
  refreshPermissions: () => Promise<void>
}

const MODULE_PERMISSION_MAP: Record<string, AdminPermission> = {
  dashboard: "dashboard",
  controle: "controle",
  "gestao-usuarios": "gestaoUsuarios",
  "gestao-pedidos": "gestaoPedidos",
  financeiro: "financeiro",
  relatorios: "relatorios",
  configuracoes: "configuracoes",
}

const PermissionsContext = createContext<PermissionsContextType>({
  permissions: null,
  profile: null,
  loading: true,
  error: null,
  hasPermission: () => false,
  hasAnyPermission: () => false,
  canAccess: () => false,
  firstAllowedPath: null,
  refreshPermissions: async () => undefined,
})

export function PermissionsProvider({ children }: { children: React.ReactNode }): React.JSX.Element {
  const { user, loading: authLoading } = useAuth()
  const [permissions, setPermissions] = useState<AdminPermissions | null>(null)
  const [profile, setProfile] = useState<AdminProfile | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const requestSequence = useRef(0)

  const loadUserPermissions = useCallback(async () => {
    const requestId = ++requestSequence.current
    if (authLoading) return
    if (!user) {
      setPermissions(null)
      setProfile(null)
      setError(null)
      setLoading(false)
      return
    }

    setLoading(true)
    setError(null)
    try {
      const response = await adminFetch("/api/auth/permissions", { cache: "no-store" })
      const data = await response.json().catch(() => ({}))
      if (!response.ok || !data.success || !data.actor) {
        throw new Error(data.error || "Acesso administrativo não provisionado")
      }
      if (requestId !== requestSequence.current) return

      setPermissions(normalizeAdminPermissions(data.actor.permissions, { inheritLegacy: true }))
      setProfile({
        uid: String(data.actor.uid ?? user.uid),
        email: String(data.actor.email ?? user.email ?? ""),
        name: String(data.actor.name ?? user.displayName ?? user.email ?? "Administrador"),
        isMaster: data.actor.isMaster === true,
      })
    } catch (loadError: unknown) {
      if (requestId !== requestSequence.current) return
      setPermissions(null)
      setProfile(null)
      setError(loadError instanceof Error ? loadError.message : "Falha ao validar permissões")
    } finally {
      if (requestId === requestSequence.current) setLoading(false)
    }
  }, [authLoading, user])

  useEffect(() => {
    void loadUserPermissions()
  }, [loadUserPermissions])

  const hasPermission = useCallback((permission: AdminPermission): boolean => {
    return permissions ? hasAdminPermission(permissions, permission) : false
  }, [permissions])

  const hasAnyPermission = useCallback((required: readonly AdminPermission[]): boolean => {
    return permissions ? hasAnyAdminPermission(permissions, required) : false
  }, [permissions])

  const canAccess = useCallback((module: string): boolean => {
    const permission = MODULE_PERMISSION_MAP[module]
    return permission ? hasPermission(permission) : false
  }, [hasPermission])

  const landingPath = useMemo(
    () => permissions ? firstAllowedPath(permissions) : null,
    [permissions]
  )

  const contextValue = useMemo<PermissionsContextType>(() => ({
    permissions,
    profile,
    loading: authLoading || loading,
    error,
    hasPermission,
    hasAnyPermission,
    canAccess,
    firstAllowedPath: landingPath,
    refreshPermissions: loadUserPermissions,
  }), [
    permissions,
    profile,
    authLoading,
    loading,
    error,
    hasPermission,
    hasAnyPermission,
    canAccess,
    landingPath,
    loadUserPermissions,
  ])

  return React.createElement(
    PermissionsContext.Provider,
    { value: contextValue },
    children
  )
}

export const usePermissions = () => useContext(PermissionsContext)
