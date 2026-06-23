"use client"

import { useMemo, useState } from "react"
import { AppShell } from "@/components/layout/app-shell"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { UsersTable } from "@/components/users/users-table"
import { UserModal } from "@/components/users/user-modal"
import { useUsers, useAllClients } from "@/hooks/use-users"
import { useUsersDebug } from "@/hooks/use-users-debug"
import { Badge } from "@/components/ui/badge"
import {
  User,
  UserCheck,
  UserX,
  Search,
  Download,
  Plus,
  RefreshCw,
  Users as UsersIcon,
  TrendingUp,
} from "lucide-react"
import { useToast } from "@/hooks/use-toast"
import { PageWithBack } from "@/components/layout/page-with-back"
import { usePermissions } from "@/hooks/use-permissions"

export default function ClientsPage() {
  const [search, setSearch] = useState("")
  const [modalOpen, setModalOpen] = useState(false)
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<string>("all")
  const { toast } = useToast()
  const { hasPermission } = usePermissions()
  const canAdministerUsers = hasPermission("administrarUsuarios")

  // Hook de debug para ver todos os usuários
  const { allUsers, loading: debugLoading, refetch: refetchDebug } = useUsersDebug()
  
  // Hook específico para buscar todos os clientes
  const { clients: allClients, loading: clientsLoading, refetch: refetchClients } = useAllClients()

  const filters = {
    userType: 'client',
    searchTerm: search || undefined,
    isActive: statusFilter === "all" ? undefined : statusFilter === "active"
  }

  const { 
    users, 
    loading, 
    refetch, 
    createUser, 
    updateUser, 
    deleteUser, 
    toggleUserStatus, 
    blockUser, 
    unblockUser 
  } = useUsers(filters)

  // Buscar clientes de múltiplas formas para garantir que todos sejam encontrados
  const clientUsers = useMemo(() => {
    // Prioridade 1: Usar hook específico de clientes se disponível
    if (allClients.length > 0) {
      let filteredClients = allClients
      
      // Aplicar filtro de busca se existir
      if (search) {
        const searchLower = search.toLowerCase()
        filteredClients = filteredClients.filter(user => {
          const dynamicUser = user as unknown as Record<string, unknown>
          const nome = typeof dynamicUser.nome === "string" ? dynamicUser.nome.toLowerCase() : ""
          const cpf = typeof dynamicUser.cpf === "string" ? dynamicUser.cpf.toLowerCase() : ""
          const document = typeof dynamicUser.document === "string" ? dynamicUser.document.toLowerCase() : ""

          return (
            user.fullName?.toLowerCase().includes(searchLower) ||
            user.name?.toLowerCase().includes(searchLower) ||
            nome.includes(searchLower) ||
            user.email?.toLowerCase().includes(searchLower) ||
            cpf.includes(searchLower) ||
            document.includes(searchLower)
          )
        })
      }
      
      // Aplicar filtro de status se não for "all"
      if (statusFilter !== "all") {
        const isActive = statusFilter === "active"
        filteredClients = filteredClients.filter(user => 
          (user.isActive !== false) === isActive
        )
      }
      
      return filteredClients
    }
    
    // Prioridade 2: Usar usuários filtrados se existirem
    if (users.length > 0) {
      return users
    }
    
    // Prioridade 3: Buscar manualmente em todos os usuários
    const manualClients = allUsers.filter(user => {
      const isClient = user.userType === 'client' || 
                      user.role === 'cliente' || 
                      user.role === 'client' ||
                      (user.userType === 'client')
      
      // Aplicar filtro de busca se existir
      if (search) {
        const searchLower = search.toLowerCase()
        const matchesSearch = user.fullName?.toLowerCase().includes(searchLower) ||
                             user.name?.toLowerCase().includes(searchLower) ||
                             user.nome?.toLowerCase().includes(searchLower) ||
                             user.email?.toLowerCase().includes(searchLower) ||
                             user.cpf?.toLowerCase().includes(searchLower) ||
                             user.document?.toLowerCase().includes(searchLower)
        return isClient && matchesSearch
      }
      
      // Aplicar filtro de status se não for "all"
      if (statusFilter !== "all") {
        const isActive = user.isActive !== false
        return isClient && (statusFilter === "active" ? isActive : !isActive)
      }
      
      return isClient
    })
    
    return manualClients
  }, [users, allClients, allUsers, search, statusFilter])

  const selectedUser = useMemo(() => clientUsers.find(u => u.id === selectedUserId) || null, [clientUsers, selectedUserId])

  const activeUsers = clientUsers.filter(u => u.isActive !== false).length
  const blockedUsers = clientUsers.filter(u => u.isActive === false).length
  const recentUsers = clientUsers.filter(u => {
    if (!u.createdAt) return false
    const thirtyDaysAgo = new Date(Date.now() - 30 * 24 * 60 * 60 * 1000)
    return u.createdAt.toDate() >= thirtyDaysAgo
  }).length

  const handleCreateUser = async (data: any) => {
    try {
      await createUser({ 
        ...data, 
        userType: 'client', 
        isActive: true,
        name: data.nome || data.name || '',
        email: data.email || ''
      })
      toast({
        title: "Sucesso!",
        description: "Cliente criado com sucesso.",
      })
      setModalOpen(false)
    } catch (error) {
      toast({
        title: "Erro",
        description: "Erro ao criar cliente. Tente novamente.",
        variant: "destructive",
      })
    }
  }

  const handleUpdateUser = async (data: any) => {
    if (!selectedUser) return
    try {
      await updateUser(selectedUser.id, data)
      toast({
        title: "Sucesso!",
        description: "Cliente atualizado com sucesso.",
      })
      setModalOpen(false)
    } catch (error) {
      toast({
        title: "Erro",
        description: "Erro ao atualizar cliente. Tente novamente.",
        variant: "destructive",
      })
    }
  }

  const handleDeleteUser = async (userId: string) => {
    try {
      await deleteUser(userId)
      toast({
        title: "Sucesso!",
        description: "Cliente excluído com sucesso.",
      })
    } catch (error) {
      toast({
        title: "Erro",
        description: "Erro ao excluir cliente. Tente novamente.",
        variant: "destructive",
      })
    }
  }

  const handleToggleStatus = async (userId: string, isActive: boolean) => {
    try {
      await toggleUserStatus(userId, isActive)
      toast({
        title: "Sucesso!",
        description: `Cliente ${isActive ? 'ativado' : 'desativado'} com sucesso.`,
      })
    } catch (error) {
      toast({
        title: "Erro",
        description: "Erro ao alterar status do cliente. Tente novamente.",
        variant: "destructive",
      })
    }
  }

  return (
    <AppShell>
      <PageWithBack backButtonLabel="Voltar para Dashboard">
        {/* Header */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="flex items-center gap-3">
              <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
                <UsersIcon className="h-5 w-5 text-primary" />
              </div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">Pessoas cadastradas</h1>
            </div>
            <p className="mt-1 text-sm text-muted-foreground ml-12">
              Pesquise e gerencie todas as pessoas cadastradas no sistema
            </p>
          </div>
          <div className="flex flex-col sm:flex-row gap-3 w-full sm:w-auto">
            <Button
              variant="outline"
              size="sm"
              onClick={() => { refetch(); refetchDebug(); refetchClients(); }}
            >
              <RefreshCw className="h-4 w-4 mr-1.5" />
              Atualizar
            </Button>
            <Button
              variant="outline"
              size="sm"
            >
              <Download className="h-4 w-4 mr-1.5" />
              Exportar
            </Button>
            {canAdministerUsers && (
              <Button
                size="sm"
                onClick={() => { setSelectedUserId(null); setModalOpen(true) }}
                className="bg-primary hover:bg-primary-hover text-primary-foreground shadow-primary"
              >
                <Plus className="h-4 w-4 mr-1.5" />
                Novo Cliente
              </Button>
            )}
          </div>
        </div>

        {/* Stats Cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
          {[
            { label: "Total de Clientes", value: users.length,   icon: User,      iconBg: "bg-blue-50 dark:bg-blue-950/40",    iconCl: "text-blue-600"    },
            { label: "Clientes Ativos",   value: activeUsers,    icon: UserCheck, iconBg: "bg-emerald-50 dark:bg-emerald-950/40", iconCl: "text-emerald-600" },
            { label: "Bloqueados",        value: blockedUsers,   icon: UserX,     iconBg: "bg-red-50 dark:bg-red-950/40",      iconCl: "text-red-600"     },
            { label: "Novos (30 dias)",   value: recentUsers,    icon: TrendingUp,iconBg: "bg-violet-50 dark:bg-violet-950/40",iconCl: "text-violet-600"  },
          ].map(({ label, value, icon: Icon, iconBg, iconCl }) => (
            <Card key={label} className="shadow-card hover:shadow-card-hover transition-shadow">
              <CardContent className="p-5 flex items-start justify-between">
                <div>
                  <p className="text-xs font-medium text-muted-foreground">{label}</p>
                  <p className="text-2xl font-bold text-foreground tabular-nums mt-1">{value}</p>
                </div>
                <div className={`h-9 w-9 rounded-lg flex items-center justify-center shrink-0 ${iconBg}`}>
                  <Icon className={`h-4 w-4 ${iconCl}`} />
                </div>
              </CardContent>
            </Card>
          ))}
        </div>

        {/* Filters */}
        <Card className="shadow-card">
          <CardContent className="p-4">
            <div className="flex flex-col sm:flex-row gap-3">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground pointer-events-none" />
                <Input
                  placeholder="Buscar por nome, email, CPF..."
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  className="pl-9 h-9 text-sm"
                />
              </div>
              <div className="flex gap-1.5">
                {[
                  { value: "all",      label: "Todos"      },
                  { value: "active",   label: "Ativos"     },
                  { value: "inactive", label: "Bloqueados" },
                ].map(({ value, label }) => (
                  <Button
                    key={value}
                    variant={statusFilter === value ? "default" : "outline"}
                    size="sm"
                    onClick={() => setStatusFilter(value)}
                    className="h-9"
                  >
                    {label}
                  </Button>
                ))}
              </div>
            </div>
          </CardContent>
        </Card>


        {/* Users Table */}
        <UsersTable 
          users={clientUsers}
          loading={loading || debugLoading || clientsLoading}
          onView={(id) => { setSelectedUserId(id); setModalOpen(true) }}
          onEdit={canAdministerUsers ? (id) => { setSelectedUserId(id); setModalOpen(true) } : undefined}
          onDelete={canAdministerUsers ? handleDeleteUser : undefined}
          onToggleStatus={canAdministerUsers ? (id, isActive) => handleToggleStatus(id, isActive) : undefined}
          onBlock={canAdministerUsers ? (id) => blockUser(id) : undefined}
          onUnblock={canAdministerUsers ? (id) => unblockUser(id) : undefined}
        />

        {/* User Modal */}
        <UserModal 
          user={selectedUser}
          isOpen={modalOpen}
          onClose={() => setModalOpen(false)}
          onSave={selectedUser ? handleUpdateUser : handleCreateUser}
          mode={selectedUser ? (canAdministerUsers ? 'edit' : 'view') : 'create'}
        />
      </PageWithBack>
    </AppShell>
  )
}
