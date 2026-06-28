"use client"

import { useEffect, useMemo, useState } from "react"
import { PageWithBack } from "@/components/layout/page-with-back"
import { ServiceChecklistPanel } from "@/components/orders/service-checklist-panel"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { adminFetch } from "@/lib/admin-api"
import { Camera, ClipboardList, KeyRound, PackageSearch, Search } from "lucide-react"

interface ChecklistRow {
  id: string
  orderId: string
  protocol: string
  orderStatus: string
  checklistStatus: string
  checklistStatusLabel: string
  serviceName: string
  serviceType: string
  clientId: string
  clientName: string
  providerId: string
  providerName: string
  startedAt: string | null
  completedAt: string | null
  updatedAt: string | null
  materialsUsed: boolean
  materialsDescription: string
  photosBeforeCount: number
  photosDuringCount: number
  photosAfterCount: number
  totalPhotos: number
  problemResolution: string
}

function formatDate(value: string | null): string {
  if (!value) return "-"
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return "-"
  return new Intl.DateTimeFormat("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  }).format(date)
}

function statusBadge(status: string) {
  if (status === "ready_for_completion_code") return "bg-amber-100 text-amber-800"
  if (status === "completed") return "bg-green-100 text-green-700"
  if (status === "photos_pending") return "bg-blue-100 text-blue-700"
  if (status === "checklist_pending") return "bg-muted text-muted-foreground"
  return "bg-slate-100 text-slate-700"
}

export default function ChecklistsPage() {
  const [rows, setRows] = useState<ChecklistRow[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState("")
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null)

  async function loadChecklists() {
    setLoading(true)
    setError(null)
    try {
      const response = await adminFetch("/api/checklists?limit=300", { cache: "no-store" })
      const data = await response.json().catch(() => ({}))
      if (!response.ok || !data.success) {
        throw new Error(data.error || "Falha ao carregar checklists")
      }
      setRows(Array.isArray(data.checklists) ? data.checklists : [])
    } catch (err) {
      setError(err instanceof Error ? err.message : "Falha ao carregar checklists")
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void loadChecklists()
  }, [])

  const filtered = useMemo(() => {
    const term = search.trim().toLowerCase()
    if (!term) return rows
    return rows.filter((row) =>
      [
        row.protocol,
        row.orderId,
        row.clientName,
        row.clientId,
        row.providerName,
        row.providerId,
        row.serviceName,
        row.serviceType,
        row.checklistStatusLabel,
        row.materialsDescription,
      ]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(term))
    )
  }, [rows, search])

  const stats = useMemo(() => {
    return {
      total: rows.length,
      ready: rows.filter((row) => row.checklistStatus === "ready_for_completion_code").length,
      completed: rows.filter((row) => row.orderStatus === "completed" || row.checklistStatus === "completed").length,
      materials: rows.filter((row) => row.materialsUsed).length,
    }
  }, [rows])

  return (
    <PageWithBack backButtonLabel="Voltar para Serviços">
        <div className="space-y-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-3">
              <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
                <ClipboardList className="h-5 w-5 text-primary" />
              </div>
              <div>
                <h1 className="text-2xl font-bold tracking-tight">Checklists de OS</h1>
                <p className="text-sm text-muted-foreground">
                  Consulte checklists, fotos, materiais usados e finalização por código do cliente.
                </p>
              </div>
            </div>
            <Button variant="outline" onClick={() => void loadChecklists()} disabled={loading}>
              Atualizar
            </Button>
          </div>

          <div className="grid gap-3 grid-cols-2 lg:grid-cols-4">
            <StatCard title="Checklists" value={stats.total} icon={<ClipboardList className="h-4 w-4" />} />
            <StatCard title="Aguardando código" value={stats.ready} icon={<KeyRound className="h-4 w-4" />} />
            <StatCard title="Completos" value={stats.completed} icon={<ClipboardList className="h-4 w-4" />} />
            <StatCard title="Com materiais" value={stats.materials} icon={<PackageSearch className="h-4 w-4" />} />
          </div>

          <Card>
            <CardHeader className="gap-3 sm:flex-row sm:items-center sm:justify-between">
              <CardTitle className="text-base">Lista de checklists</CardTitle>
              <div className="relative w-full sm:w-96">
                <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder="Buscar pedido, cliente, prestador, serviço..."
                  className="pl-9"
                />
              </div>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="space-y-2">
                  {Array.from({ length: 6 }).map((_, index) => (
                    <Skeleton key={index} className="h-12 w-full" />
                  ))}
                </div>
              ) : error ? (
                <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-sm text-destructive">
                  {error}
                </div>
              ) : filtered.length === 0 ? (
                <div className="py-10 text-center text-sm text-muted-foreground">
                  Nenhum checklist encontrado para os filtros atuais.
                </div>
              ) : (
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>Pedido</TableHead>
                        <TableHead>Serviço</TableHead>
                        <TableHead>Cliente</TableHead>
                        <TableHead>Prestador</TableHead>
                        <TableHead>Status</TableHead>
                        <TableHead>Fotos</TableHead>
                        <TableHead>Materiais</TableHead>
                        <TableHead>Atualizado</TableHead>
                        <TableHead className="text-right">Ação</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filtered.map((row) => (
                        <TableRow key={row.id} className={selectedOrderId === row.orderId ? "bg-muted/50" : undefined}>
                          <TableCell className="font-medium">{row.protocol}</TableCell>
                          <TableCell>{row.serviceName || "-"}</TableCell>
                          <TableCell>{row.clientName || row.clientId || "-"}</TableCell>
                          <TableCell>{row.providerName || row.providerId || "-"}</TableCell>
                          <TableCell>
                            <Badge className={statusBadge(row.checklistStatus)}>{row.checklistStatusLabel}</Badge>
                          </TableCell>
                          <TableCell>
                            <span className="inline-flex items-center gap-1 text-sm">
                              <Camera className="h-3.5 w-3.5" />
                              {row.photosBeforeCount}/{row.photosAfterCount}
                            </span>
                          </TableCell>
                          <TableCell>{row.materialsUsed ? "Sim" : "Não"}</TableCell>
                          <TableCell>{formatDate(row.updatedAt || row.startedAt)}</TableCell>
                          <TableCell className="text-right">
                            <Button size="sm" variant="outline" onClick={() => setSelectedOrderId(row.orderId)}>
                              Abrir checklist
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              )}
            </CardContent>
          </Card>

          {selectedOrderId && (
            <Card>
              <CardHeader>
                <CardTitle className="text-base">Detalhe do checklist</CardTitle>
              </CardHeader>
              <CardContent>
                <ServiceChecklistPanel orderId={selectedOrderId} />
              </CardContent>
            </Card>
          )}
        </div>
      </PageWithBack>
  )
}

function StatCard({ title, value, icon }: { title: string; value: number; icon: React.ReactNode }) {
  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
        <CardTitle className="text-xs font-medium text-muted-foreground">{title}</CardTitle>
        <span className="text-muted-foreground">{icon}</span>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-bold tabular-nums">{value}</div>
      </CardContent>
    </Card>
  )
}
