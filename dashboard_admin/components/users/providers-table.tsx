"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { Label } from "@/components/ui/label"
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog"
import { Search, Eye, CheckCircle, Shield, Star, Loader2, RefreshCw, UserX } from "lucide-react"
import { ProviderModal } from "./provider-modal"
import { FirebaseProvidersService, type FirebaseProvider } from "@/lib/services/firebase-providers"
import { mapProviderStatusToLegacy } from "@/lib/providers/status"
import { mapRawVerificationStatus } from "@/lib/verification-status"
import { toIsoStringFromUnknown } from "@/lib/date-utils"
import { adminFetch } from "@/lib/admin-api"
import { usePermissions } from "@/hooks/use-permissions"

interface Provider {
  id: string
  name: string
  email: string
  phone: string
  cpf: string
  address: string
  serviceCategories: string[]
  experience: string
  isVerified: boolean
  rating: number
  totalOrders: number
  totalEarnings: number
  status: "active" | "inactive" | "pending" | "blocked"
  blocked: boolean
  blockType: string | null
  blockedReason: string | null
  createdAt: string
}

function convertFirebaseToProvider(provider: FirebaseProvider): Provider {
  const vs = mapRawVerificationStatus((provider as any).verificationStatus)
  const isVerified = vs === "approved"
  const isBlocked = (provider as any).blocked === true
  const status = isBlocked
    ? "blocked"
    : vs === "rejected"
      ? "blocked"
      : vs === "pending"
        ? "pending"
        : mapProviderStatusToLegacy(provider.status)

  return {
    id: provider.id,
    name: provider.nome,
    email: provider.email || "",
    phone: provider.telefone || "",
    cpf: String((provider as any).cpf || (provider as any).documento || ""),
    address: String((provider as any).endereco || (provider as any).address || ""),
    serviceCategories: provider.especialidades || [],
    experience: String((provider as any).experience || (provider as any).experiencia || ""),
    isVerified,
    rating: provider.avaliacao || 0,
    totalOrders: provider.totalServicos || 0,
    totalEarnings: Number((provider as any).totalEarnings || (provider as any).totalGanhos || 0),
    status,
    blocked: isBlocked,
    blockType: (provider as any).blockType ?? null,
    blockedReason: (provider as any).blockedReason ?? null,
    createdAt: provider.createdAt ? toIsoStringFromUnknown(provider.createdAt) : "",
  }
}

const BLOCK_REASONS = [
  "Baixa avaliação",
  "Falta recorrente",
  "Descumprimento de regras",
  "Fraude",
  "Outro",
]

export function ProvidersTable() {
  const { hasPermission } = usePermissions()
  const canAdministerUsers = hasPermission("administrarUsuarios")
  const [providers, setProviders] = useState<Provider[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [updatingProviderId, setUpdatingProviderId] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState("")
  const [statusFilter, setStatusFilter] = useState<string>("all")
  const [categoryFilter, setCategoryFilter] = useState<string>("all")
  const [selectedProvider, setSelectedProvider] = useState<Provider | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)

  // Bloqueio de prestador
  const [blockTarget, setBlockTarget] = useState<Provider | null>(null)
  const [blockType, setBlockType] = useState<"suspension" | "permanent">("suspension")
  const [blockReason, setBlockReason] = useState<string>(BLOCK_REASONS[0])
  const [blockReasonDetail, setBlockReasonDetail] = useState("")
  const [blockedUntil, setBlockedUntil] = useState("")
  const [blocking, setBlocking] = useState(false)

  const fetchProviders = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const firebaseProviders = await FirebaseProvidersService.getProviders()
      setProviders(firebaseProviders.map(convertFirebaseToProvider))
    } catch (err) {
      console.error("Erro ao buscar prestadores:", err)
      setError("Erro ao carregar prestadores")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchProviders()
  }, [fetchProviders])

  const availableCategories = useMemo(() => {
    return Array.from(
      new Set(
        providers.flatMap((provider) => provider.serviceCategories).filter(Boolean)
      )
    ).sort((left, right) => left.localeCompare(right, "pt-BR"))
  }, [providers])

  const filteredProviders = providers.filter((provider) => {
    const normalizedSearch = searchTerm.trim().toLowerCase()
    const matchesSearch =
      !normalizedSearch ||
      provider.name.toLowerCase().includes(normalizedSearch) ||
      provider.email.toLowerCase().includes(normalizedSearch) ||
      provider.phone.toLowerCase().includes(normalizedSearch) ||
      provider.cpf.includes(searchTerm)

    const matchesStatus = statusFilter === "all" || provider.status === statusFilter
    const matchesCategory =
      categoryFilter === "all" || provider.serviceCategories.some((category) => category === categoryFilter)

    return matchesSearch && matchesStatus && matchesCategory
  })

  const getStatusBadge = (status: Provider["status"]) => {
    switch (status) {
      case "active":
        return <Badge className="bg-green-100 text-green-800">Ativo</Badge>
      case "inactive":
        return <Badge className="bg-muted text-muted-foreground">Inativo</Badge>
      case "pending":
        return <Badge className="bg-orange-100 text-orange-800">Pendente</Badge>
      case "blocked":
        return <Badge className="bg-red-100 text-red-800">Bloqueado</Badge>
      default:
        return <Badge>Desconhecido</Badge>
    }
  }

  const handleViewProvider = (provider: Provider) => {
    setSelectedProvider(provider)
    setIsModalOpen(true)
  }

  const openBlockDialog = (provider: Provider) => {
    setBlockTarget(provider)
    setBlockType("suspension")
    setBlockReason(BLOCK_REASONS[0])
    setBlockReasonDetail("")
    setBlockedUntil("")
  }

  const submitBlock = async () => {
    if (!blockTarget) return
    const reason = blockReason === "Outro" ? blockReasonDetail.trim() : blockReason
    if (!reason) {
      setError("Informe o motivo do bloqueio")
      return
    }
    try {
      setBlocking(true)
      setError(null)
      const res = await adminFetch(`/api/providers/${blockTarget.id}/block`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          blockType,
          reason,
          blockedUntil: blockType === "suspension" && blockedUntil ? new Date(blockedUntil).toISOString() : undefined,
        }),
      })
      if (!res.ok) throw new Error((await res.json()).error || "Falha ao bloquear")
      setBlockTarget(null)
      await fetchProviders()
    } catch (err) {
      console.error("Erro ao bloquear prestador:", err)
      setError(err instanceof Error ? err.message : "Erro ao bloquear prestador")
    } finally {
      setBlocking(false)
    }
  }

  const handleUnblock = async (provider: Provider) => {
    try {
      setUpdatingProviderId(provider.id)
      setError(null)
      const res = await adminFetch(`/api/providers/${provider.id}/block`, { method: "DELETE" })
      if (!res.ok) throw new Error((await res.json()).error || "Falha ao desbloquear")
      await fetchProviders()
    } catch (err) {
      console.error("Erro ao desbloquear prestador:", err)
      setError(err instanceof Error ? err.message : "Erro ao desbloquear prestador")
    } finally {
      setUpdatingProviderId(null)
    }
  }

  const handleProviderCategoriesUpdated = (categories: string[]) => {
    if (!selectedProvider) {
      return
    }

    setSelectedProvider({
      ...selectedProvider,
      serviceCategories: categories,
    })

    setProviders((current) =>
      current.map((provider) =>
        provider.id === selectedProvider.id
          ? {
              ...provider,
              serviceCategories: categories,
            }
          : provider
      )
    )
  }

  return (
    <>
      <Card>
        <CardHeader>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle>Prestadores cadastrados</CardTitle>
              <CardDescription>
                {filteredProviders.length} de {providers.length} prestadores exibidos
              </CardDescription>
            </div>
            <Button variant="outline" onClick={fetchProviders} disabled={loading}>
              <RefreshCw className={`mr-2 h-4 w-4 ${loading ? "animate-spin" : ""}`} />
              Atualizar
            </Button>
          </div>
        </CardHeader>
        <CardContent>
          <div className="mb-6 flex flex-col gap-4 sm:flex-row">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Buscar por nome, email, telefone ou CPF"
                value={searchTerm}
                onChange={(event) => setSearchTerm(event.target.value)}
                className="pl-10"
              />
            </div>
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger className="w-full sm:w-48">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos os status</SelectItem>
                <SelectItem value="active">Ativo</SelectItem>
                <SelectItem value="inactive">Inativo</SelectItem>
                <SelectItem value="pending">Pendente</SelectItem>
                <SelectItem value="blocked">Bloqueado</SelectItem>
              </SelectContent>
            </Select>
            <Select value={categoryFilter} onValueChange={setCategoryFilter}>
              <SelectTrigger className="w-full sm:w-56">
                <SelectValue placeholder="Categoria" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todas as categorias</SelectItem>
                {availableCategories.map((category) => (
                  <SelectItem key={category} value={category}>
                    {category}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          {loading ? (
            <div className="flex items-center justify-center py-12">
              <Loader2 className="h-8 w-8 animate-spin text-orange-500" />
              <span className="ml-3 text-muted-foreground">Carregando prestadores...</span>
            </div>
          ) : null}

          {error && !loading ? (
            <div className="py-12 text-center">
              <p className="font-medium text-red-600">{error}</p>
              <Button variant="outline" className="mt-4" onClick={fetchProviders}>
                Tentar novamente
              </Button>
            </div>
          ) : null}

          {!loading && !error && filteredProviders.length === 0 ? (
            <div className="py-12 text-center">
              <p className="font-medium text-muted-foreground">Nenhum prestador encontrado</p>
              <p className="mt-1 text-sm text-muted-foreground">Ajuste os filtros para ampliar a busca.</p>
            </div>
          ) : null}

          {!loading && !error && filteredProviders.length > 0 ? (
            <div className="rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Prestador</TableHead>
                    <TableHead>Contato</TableHead>
                    <TableHead>Categorias</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead>Avaliacao</TableHead>
                    <TableHead>Pedidos</TableHead>
                    <TableHead>Ganhos</TableHead>
                    <TableHead className="text-right">Acoes</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredProviders.map((provider) => (
                    <TableRow key={provider.id}>
                      <TableCell>
                        <div>
                          <div className="flex items-center gap-2 font-medium">
                            {provider.name}
                            {provider.isVerified ? <Shield className="h-4 w-4 text-blue-600" /> : null}
                          </div>
                          <div className="text-sm text-muted-foreground">{provider.cpf || "Sem CPF"}</div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div>
                          <div className="text-sm">{provider.email || "Sem email"}</div>
                          <div className="text-sm text-muted-foreground">{provider.phone || "Sem telefone"}</div>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {provider.serviceCategories.length > 0 ? (
                            provider.serviceCategories.map((category) => (
                              <Badge key={category} variant="outline" className="text-xs">
                                {category}
                              </Badge>
                            ))
                          ) : (
                            <span className="text-sm text-muted-foreground">Sem categorias</span>
                          )}
                        </div>
                      </TableCell>
                      <TableCell>{getStatusBadge(provider.status)}</TableCell>
                      <TableCell>
                        <div className="flex items-center gap-1">
                          <Star className="h-4 w-4 fill-current text-yellow-400" />
                          <span>{provider.rating.toFixed(1)}</span>
                        </div>
                      </TableCell>
                      <TableCell>{provider.totalOrders}</TableCell>
                      <TableCell>
                        {provider.totalEarnings > 0
                          ? `R$ ${provider.totalEarnings.toLocaleString("pt-BR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
                          : "N/A"}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-2">
                          <Button variant="ghost" size="sm" onClick={() => handleViewProvider(provider)}>
                            <Eye className="h-4 w-4" />
                          </Button>
                          {canAdministerUsers && (provider.blocked ? (
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Desbloquear prestador"
                              onClick={() => handleUnblock(provider)}
                              disabled={updatingProviderId === provider.id}
                            >
                              {updatingProviderId === provider.id
                                ? <Loader2 className="h-4 w-4 animate-spin" />
                                : <CheckCircle className="h-4 w-4 text-green-600" />}
                            </Button>
                          ) : (
                            <Button
                              variant="ghost"
                              size="sm"
                              title="Suspender / bloquear prestador"
                              onClick={() => openBlockDialog(provider)}
                              disabled={updatingProviderId === provider.id}
                            >
                              <UserX className="h-4 w-4 text-red-600" />
                            </Button>
                          ))}
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <ProviderModal
        provider={selectedProvider}
        isOpen={isModalOpen}
        onUpdated={fetchProviders}
        onCategoriesUpdated={handleProviderCategoriesUpdated}
        onClose={() => {
          setIsModalOpen(false)
          setSelectedProvider(null)
        }}
      />

      <Dialog open={!!blockTarget} onOpenChange={(open) => { if (!open) setBlockTarget(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <UserX className="h-5 w-5 text-red-600" />
              Bloquear prestador
            </DialogTitle>
            <DialogDescription>
              {blockTarget?.name} — o prestador não poderá fazer login nem receber novos serviços.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="space-y-2">
              <Label>Tipo de bloqueio</Label>
              <Select value={blockType} onValueChange={(v) => setBlockType(v as "suspension" | "permanent")}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="suspension">Suspensão (temporária)</SelectItem>
                  <SelectItem value="permanent">Bloqueio definitivo</SelectItem>
                </SelectContent>
              </Select>
            </div>

            {blockType === "suspension" ? (
              <div className="space-y-2">
                <Label>Suspenso até (opcional)</Label>
                <Input type="date" value={blockedUntil} onChange={(e) => setBlockedUntil(e.target.value)} />
              </div>
            ) : null}

            <div className="space-y-2">
              <Label>Motivo</Label>
              <Select value={blockReason} onValueChange={setBlockReason}>
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {BLOCK_REASONS.map((reason) => (
                    <SelectItem key={reason} value={reason}>{reason}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {blockReason === "Outro" ? (
              <div className="space-y-2">
                <Label>Descreva o motivo</Label>
                <Textarea
                  value={blockReasonDetail}
                  onChange={(e) => setBlockReasonDetail(e.target.value)}
                  placeholder="Descreva o motivo do bloqueio"
                  rows={3}
                />
              </div>
            ) : null}
          </div>

          <DialogFooter>
            <Button variant="outline" onClick={() => setBlockTarget(null)} disabled={blocking}>
              Cancelar
            </Button>
            <Button
              className="bg-red-600 hover:bg-red-700 text-white"
              onClick={submitBlock}
              disabled={blocking}
            >
              {blocking ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : null}
              {blockType === "suspension" ? "Suspender" : "Bloquear"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
