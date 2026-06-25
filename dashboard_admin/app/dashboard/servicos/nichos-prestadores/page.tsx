"use client"

import { useCallback, useEffect, useState } from "react"
import { PageWithBack } from "@/components/layout/page-with-back"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { Input } from "@/components/ui/input"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { CheckCircle, XCircle, Clock, RefreshCw, Layers, FileText, Search, Users, ChevronLeft, ChevronRight, User } from "lucide-react"
import { adminFetch } from "@/lib/admin-api"

interface Provider {
  id: string
  fullName: string
  email: string
  phone: string
  cpf: string
  services: string[]
  rating: number
  isActive: boolean
  verificationStatus: string
  createdAt: { _seconds: number } | null
  profileImageUrl: string
}

interface SpecialtyRequest {
  id: string
  providerId: string
  providerName: string
  requestedServices: string[]
  currentServices: string[]
  justificationText?: string
  documentPhotoUrl?: string
  selfiePhotoUrl?: string
  proofPhotoUrls?: string[]
  documentUrls?: string[]
  status: "pending" | "approved" | "rejected"
  rejectionReason?: string
  createdAt: { seconds: number, _seconds?: number } | null
  reviewedAt?: { seconds: number, _seconds?: number } | null
}

function fmtDate(ts: any) {
  if (!ts) return "—"
  const seconds = ts._seconds || ts.seconds
  if (!seconds) return "—"
  return new Date(seconds * 1000).toLocaleString("pt-BR")
}

const STATUS_CONFIG = {
  pending: { label: "Pendente", variant: "secondary" as const, icon: Clock },
  approved: { label: "Aprovada", variant: "default" as const, icon: CheckCircle },
  rejected: { label: "Rejeitada", variant: "destructive" as const, icon: XCircle },
}

const NICHES = [
  "Todos", "Elétrica", "Encanador", "Instalação", "Caixa d'água", 
  "Desentupimento manual", "Desentupimento com maquinário até 2 m", 
  "Caça-vazamentos", "Limpeza de estofados", "Ar condicionado", 
  "Eletrodomésticos", "Chaveiro residencial", "Serviços automotivos", 
  "Montagem de móveis", "Faxina"
]

export default function NichosPrestadoresPage() {
  // Aba 1: Prestadores
  const [providers, setProviders] = useState<Provider[]>([])
  const [provLoading, setProvLoading] = useState(true)
  const [searchName, setSearchName] = useState("")
  const [filterNiche, setFilterNiche] = useState("Todos")
  const [filterStatus, setFilterStatus] = useState("Todos")
  const [filterVerif, setFilterVerif] = useState("Todos")
  const [page, setPage] = useState(1)
  const [totalProv, setTotalProv] = useState(0)

  // Aba 2: Solicitações
  const [requests, setRequests] = useState<SpecialtyRequest[]>([])
  const [reqLoading, setReqLoading] = useState(true)
  const [reqFilter, setReqFilter] = useState<"pending" | "approved" | "rejected" | "all">("pending")
  const [rejectDialog, setRejectDialog] = useState<{ open: boolean; requestId: string } | null>(null)
  const [rejectionReason, setRejectionReason] = useState("")
  const [submitting, setSubmitting] = useState<string | null>(null)

  const loadProviders = useCallback(async () => {
    setProvLoading(true)
    try {
      const q = new URLSearchParams()
      q.set('niche', filterNiche)
      q.set('name', searchName)
      q.set('status', filterStatus)
      q.set('verification', filterVerif)
      q.set('page', page.toString())
      q.set('limit', '20')

      const res = await adminFetch(`/api/providers-niches?${q.toString()}`)
      const json = await res.json()
      if (json.success) {
        setProviders(json.providers)
        setTotalProv(json.total)
      }
    } finally {
      setProvLoading(false)
    }
  }, [filterNiche, searchName, filterStatus, filterVerif, page])

  const loadRequests = useCallback(async () => {
    setReqLoading(true)
    try {
      const res = await adminFetch(`/api/specialty-requests?status=${reqFilter}`)
      const json = await res.json()
      if (json.success) setRequests(json.requests)
    } finally {
      setReqLoading(false)
    }
  }, [reqFilter])

  useEffect(() => {
    const t = setTimeout(() => loadProviders(), 500)
    return () => clearTimeout(t)
  }, [loadProviders])

  useEffect(() => { loadRequests() }, [loadRequests])

  async function actRequest(requestId: string, action: "approve" | "reject", reason?: string) {
    setSubmitting(requestId)
    try {
      const res = await adminFetch("/api/specialty-requests", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ requestId, action, rejectionReason: reason }),
      })
      const json = await res.json()
      if (json.success) {
        await loadRequests()
        await loadProviders() // atualiza aba 1 se aprovou
      } else {
        alert(json.error ?? "Erro ao processar solicitação")
      }
    } finally {
      setSubmitting(null)
      setRejectDialog(null)
      setRejectionReason("")
    }
  }

  const pendingCount = requests.filter(r => r.status === "pending").length
  const totalPages = Math.ceil(totalProv / 20)

  return (
    <PageWithBack backButtonLabel="Voltar">
      <div className="space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-primary/10 rounded-lg">
              <Users className="w-6 h-6 text-primary" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight">Nichos dos Prestadores</h1>
              <p className="text-muted-foreground">Gestão de especialidades e visualização por nicho.</p>
            </div>
          </div>
        </div>

        <Tabs defaultValue="providers" className="w-full">
          <TabsList className="mb-4">
            <TabsTrigger value="providers">Prestadores por Nicho</TabsTrigger>
            <TabsTrigger value="requests" className="relative">
              Solicitações de Alteração
              {pendingCount > 0 && (
                <span className="ml-2 bg-destructive text-destructive-foreground text-[10px] font-bold px-2 py-0.5 rounded-full">
                  {pendingCount}
                </span>
              )}
            </TabsTrigger>
          </TabsList>

          {/* ABA 1: PRESTADORES */}
          <TabsContent value="providers" className="space-y-4">
            <Card>
              <CardContent className="p-4">
                <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
                  <div className="relative">
                    <Search className="absolute left-2.5 top-2.5 h-4 w-4 text-muted-foreground" />
                    <Input
                      placeholder="Buscar por nome..."
                      className="pl-9"
                      value={searchName}
                      onChange={(e) => { setSearchName(e.target.value); setPage(1) }}
                    />
                  </div>
                  <Select value={filterNiche} onValueChange={(v) => { setFilterNiche(v); setPage(1) }}>
                    <SelectTrigger><SelectValue placeholder="Nicho" /></SelectTrigger>
                    <SelectContent>
                      {NICHES.map(n => <SelectItem key={n} value={n}>{n}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <Select value={filterStatus} onValueChange={(v) => { setFilterStatus(v); setPage(1) }}>
                    <SelectTrigger><SelectValue placeholder="Status da Conta" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Todos">Todos (Status)</SelectItem>
                      <SelectItem value="Ativo">Ativos</SelectItem>
                      <SelectItem value="Inativo">Inativos</SelectItem>
                    </SelectContent>
                  </Select>
                  <Select value={filterVerif} onValueChange={(v) => { setFilterVerif(v); setPage(1) }}>
                    <SelectTrigger><SelectValue placeholder="Verificação" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="Todos">Todos (Verificação)</SelectItem>
                      <SelectItem value="Pendente">Pendentes</SelectItem>
                      <SelectItem value="Aprovado">Aprovados</SelectItem>
                      <SelectItem value="Rejeitado">Rejeitados</SelectItem>
                    </SelectContent>
                  </Select>
                  <Button variant="outline" onClick={loadProviders} disabled={provLoading}>
                    <RefreshCw className={`w-4 h-4 mr-2 ${provLoading ? 'animate-spin' : ''}`} />
                    Atualizar
                  </Button>
                </div>
              </CardContent>
            </Card>

            <Card>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Prestador</TableHead>
                    <TableHead>Contato</TableHead>
                    <TableHead>Nichos</TableHead>
                    <TableHead>Avaliação</TableHead>
                    <TableHead>Verificação</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {providers.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={5} className="text-center py-8 text-muted-foreground">
                        {provLoading ? "Carregando..." : "Nenhum prestador encontrado."}
                      </TableCell>
                    </TableRow>
                  ) : (
                    providers.map((p) => (
                      <TableRow key={p.id}>
                        <TableCell>
                          <div className="flex items-center gap-3">
                            {p.profileImageUrl ? (
                              <img src={p.profileImageUrl} alt="Avatar" className="w-8 h-8 rounded-full object-cover" />
                            ) : (
                              <div className="w-8 h-8 rounded-full bg-secondary flex items-center justify-center">
                                <User className="w-4 h-4 text-muted-foreground" />
                              </div>
                            )}
                            <div>
                              <p className="font-medium">{p.fullName}</p>
                              <div className="flex items-center gap-2 mt-1">
                                <Badge variant={p.isActive ? "default" : "secondary"} className="text-[10px] px-1 py-0 h-4">
                                  {p.isActive ? "Ativo" : "Inativo"}
                                </Badge>
                              </div>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell>
                          <p className="text-sm">{p.email}</p>
                          <p className="text-xs text-muted-foreground">{p.phone}</p>
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-wrap gap-1 max-w-[250px]">
                            {p.services.length > 0 ? (
                              p.services.map(s => (
                                <Badge key={s} variant="outline" className="text-xs">{s}</Badge>
                              ))
                            ) : (
                              <span className="text-xs text-muted-foreground">Nenhum</span>
                            )}
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex items-center gap-1">
                            ⭐ <span className="font-medium">{p.rating.toFixed(1)}</span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <Badge variant={p.verificationStatus === 'approved' ? 'default' : p.verificationStatus === 'pending' ? 'secondary' : 'destructive'}>
                            {p.verificationStatus === 'approved' ? 'Aprovado' : p.verificationStatus === 'pending' ? 'Pendente' : 'Rejeitado'}
                          </Badge>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
              
              {/* Pagination */}
              {totalPages > 1 && (
                <div className="flex items-center justify-between p-4 border-t">
                  <p className="text-sm text-muted-foreground">
                    Mostrando {(page - 1) * 20 + 1} até {Math.min(page * 20, totalProv)} de {totalProv} prestadores
                  </p>
                  <div className="flex items-center gap-2">
                    <Button variant="outline" size="sm" onClick={() => setPage(p => Math.max(1, p - 1))} disabled={page === 1}>
                      <ChevronLeft className="w-4 h-4" />
                    </Button>
                    <span className="text-sm font-medium px-2">Página {page} de {totalPages}</span>
                    <Button variant="outline" size="sm" onClick={() => setPage(p => Math.min(totalPages, p + 1))} disabled={page === totalPages}>
                      <ChevronRight className="w-4 h-4" />
                    </Button>
                  </div>
                </div>
              )}
            </Card>
          </TabsContent>

          {/* ABA 2: SOLICITAÇÕES */}
          <TabsContent value="requests" className="space-y-4">
            <Card>
              <CardContent className="p-4 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">Filtrar:</span>
                  <Select value={reqFilter} onValueChange={(v: any) => setReqFilter(v)}>
                    <SelectTrigger className="w-[180px]">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="pending">Pendentes</SelectItem>
                      <SelectItem value="approved">Aprovadas</SelectItem>
                      <SelectItem value="rejected">Rejeitadas</SelectItem>
                      <SelectItem value="all">Todas</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <Button variant="outline" onClick={loadRequests} disabled={reqLoading}>
                  <RefreshCw className={`w-4 h-4 mr-2 ${reqLoading ? 'animate-spin' : ''}`} />
                  Atualizar
                </Button>
              </CardContent>
            </Card>

            <div className="grid grid-cols-1 gap-4">
              {requests.length === 0 && (
                <div className="text-center py-12 border rounded-xl bg-card">
                  <Layers className="w-12 h-12 text-muted-foreground/30 mx-auto mb-4" />
                  <p className="text-muted-foreground text-lg">Nenhuma solicitação encontrada.</p>
                </div>
              )}

              {requests.map(req => {
                const conf = STATUS_CONFIG[req.status]
                const StatusIcon = conf.icon
                
                // Diff logic
                const currentSet = new Set(req.currentServices || [])
                const requestedSet = new Set(req.requestedServices || [])
                const allServices = Array.from(new Set([...currentSet, ...requestedSet]))
                
                return (
                  <Card key={req.id} className="overflow-hidden">
                    <CardHeader className="bg-muted/30 pb-4">
                      <div className="flex justify-between items-start">
                        <div>
                          <CardTitle className="text-lg">{req.providerName}</CardTitle>
                          <CardDescription className="mt-1">
                            ID: {req.providerId}<br/>
                            Data: {fmtDate(req.createdAt)}
                          </CardDescription>
                        </div>
                        <Badge variant={conf.variant} className="flex items-center gap-1.5 px-3 py-1">
                          <StatusIcon className="w-4 h-4" />
                          {conf.label}
                        </Badge>
                      </div>
                    </CardHeader>
                    
                    <CardContent className="pt-6 space-y-6">
                      {/* Diff view */}
                      <div>
                        <h4 className="text-sm font-medium mb-3 text-muted-foreground">Alteração Solicitada (Nichos):</h4>
                        <div className="flex flex-wrap gap-2">
                          {allServices.length === 0 && <span className="text-sm text-muted-foreground">Nenhum nicho</span>}
                          {allServices.map(srv => {
                            const isOld = currentSet.has(srv)
                            const isNew = requestedSet.has(srv)
                            if (isNew && !isOld) {
                              return <Badge key={srv} className="bg-green-100 text-green-800 hover:bg-green-100 border-green-200">✨ {srv}</Badge>
                            }
                            if (isOld && !isNew) {
                              return <Badge key={srv} variant="outline" className="text-destructive line-through decoration-destructive/50 border-destructive/20">{srv}</Badge>
                            }
                            return <Badge key={srv} variant="secondary">{srv}</Badge>
                          })}
                        </div>
                      </div>

                      {/* Justificativa */}
                      {req.justificationText && (
                        <div>
                          <h4 className="text-sm font-medium mb-2 text-muted-foreground">Justificativa:</h4>
                          <div className="bg-secondary/50 p-4 rounded-md text-sm border">
                            {req.justificationText}
                          </div>
                        </div>
                      )}

                      {/* Galeria Categorizada */}
                      {(req.documentPhotoUrl || req.selfiePhotoUrl || (req.proofPhotoUrls && req.proofPhotoUrls.length > 0) || (req.documentUrls && req.documentUrls.length > 0)) && (
                        <div>
                          <h4 className="text-sm font-medium mb-3 text-muted-foreground">Comprovações Anexadas:</h4>
                          <div className="flex flex-wrap gap-4">
                            {req.documentPhotoUrl && (
                              <a href={req.documentPhotoUrl} target="_blank" rel="noreferrer" className="block group">
                                <div className="w-24 h-24 border rounded-md overflow-hidden bg-muted relative">
                                  <img src={req.documentPhotoUrl} alt="Doc" className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                                </div>
                                <p className="text-xs text-center mt-1 font-medium">📄 Documento</p>
                              </a>
                            )}
                            
                            {req.selfiePhotoUrl && (
                              <a href={req.selfiePhotoUrl} target="_blank" rel="noreferrer" className="block group">
                                <div className="w-24 h-24 border rounded-md overflow-hidden bg-muted relative">
                                  <img src={req.selfiePhotoUrl} alt="Selfie" className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                                </div>
                                <p className="text-xs text-center mt-1 font-medium">🤳 Selfie</p>
                              </a>
                            )}
                            
                            {req.proofPhotoUrls && req.proofPhotoUrls.map((url, idx) => (
                              <a key={idx} href={url} target="_blank" rel="noreferrer" className="block group">
                                <div className="w-24 h-24 border rounded-md overflow-hidden bg-muted relative">
                                  <img src={url} alt="Comprovante" className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                                </div>
                                <p className="text-xs text-center mt-1 font-medium">📋 Comp. {idx+1}</p>
                              </a>
                            ))}

                            {/* Fallback para requests antigos que só tinham array de documentUrls */}
                            {!req.documentPhotoUrl && req.documentUrls && req.documentUrls.map((url, idx) => {
                              const isPdf = url.includes('.pdf') || url.includes('%2Fpdf')
                              return (
                                <a key={idx} href={url} target="_blank" rel="noreferrer" className="block group">
                                  {isPdf ? (
                                    <div className="w-24 h-24 border rounded-md flex items-center justify-center bg-blue-50 text-blue-600 group-hover:bg-blue-100 transition-colors">
                                      <FileText className="w-8 h-8" />
                                    </div>
                                  ) : (
                                    <div className="w-24 h-24 border rounded-md overflow-hidden bg-muted relative">
                                      <img src={url} alt="Anexo" className="w-full h-full object-cover group-hover:scale-105 transition-transform" />
                                    </div>
                                  )}
                                  <p className="text-xs text-center mt-1 font-medium">Anexo {idx+1}</p>
                                </a>
                              )
                            })}
                          </div>
                        </div>
                      )}

                      {/* Motivo de Rejeição */}
                      {req.status === "rejected" && req.rejectionReason && (
                        <div className="bg-destructive/10 text-destructive p-4 rounded-md border border-destructive/20">
                          <p className="text-sm font-semibold">Motivo da recusa:</p>
                          <p className="text-sm mt-1">{req.rejectionReason}</p>
                        </div>
                      )}

                      {/* Botões de Ação */}
                      {req.status === "pending" && (
                        <div className="flex gap-3 pt-4 border-t">
                          <Button 
                            className="bg-green-600 hover:bg-green-700 text-white"
                            onClick={() => actRequest(req.id, "approve")}
                            disabled={submitting === req.id}
                          >
                            <CheckCircle className="w-4 h-4 mr-2" />
                            Aprovar Alteração
                          </Button>
                          <Button 
                            variant="destructive"
                            onClick={() => setRejectDialog({ open: true, requestId: req.id })}
                            disabled={submitting === req.id}
                          >
                            <XCircle className="w-4 h-4 mr-2" />
                            Recusar
                          </Button>
                        </div>
                      )}
                    </CardContent>
                  </Card>
                )
              })}
            </div>
          </TabsContent>
        </Tabs>
      </div>

      <Dialog open={rejectDialog?.open ?? false} onOpenChange={(o) => !o && setRejectDialog(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Recusar Solicitação</DialogTitle>
            <DialogDescription>
              Tem certeza que deseja recusar esta solicitação de especialidades? Você pode informar um motivo opcional que será enviado ao prestador.
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Textarea
              placeholder="Motivo da recusa (opcional)..."
              value={rejectionReason}
              onChange={(e) => setRejectionReason(e.target.value)}
              rows={4}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectDialog(null)}>Cancelar</Button>
            <Button 
              variant="destructive" 
              onClick={() => rejectDialog && actRequest(rejectDialog.requestId, "reject", rejectionReason)}
              disabled={submitting !== null}
            >
              Confirmar Recusa
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageWithBack>
  )
}
