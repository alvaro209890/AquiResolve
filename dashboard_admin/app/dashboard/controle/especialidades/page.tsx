"use client"

import { useCallback, useEffect, useState } from "react"
import { PageWithBack } from "@/components/layout/page-with-back"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
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
import { CheckCircle, XCircle, Clock, RefreshCw, Layers } from "lucide-react"
import { adminFetch } from "@/lib/admin-api"

interface SpecialtyRequest {
  id: string
  providerId: string
  providerName: string
  requestedServices: string[]
  currentServices: string[]
  status: "pending" | "approved" | "rejected"
  rejectionReason?: string
  createdAt: { seconds: number } | null
  reviewedAt?: { seconds: number } | null
}

function fmtDate(ts: { seconds: number } | null | undefined) {
  if (!ts) return "—"
  return new Date(ts.seconds * 1000).toLocaleString("pt-BR")
}

const STATUS_CONFIG = {
  pending: { label: "Pendente", variant: "secondary" as const, icon: Clock },
  approved: { label: "Aprovada", variant: "default" as const, icon: CheckCircle },
  rejected: { label: "Rejeitada", variant: "destructive" as const, icon: XCircle },
}

export default function EspecialidadesPage() {
  const [requests, setRequests] = useState<SpecialtyRequest[]>([])
  const [loading, setLoading] = useState(true)
  const [filter, setFilter] = useState<"pending" | "approved" | "rejected" | "all">("pending")
  const [rejectDialog, setRejectDialog] = useState<{ open: boolean; requestId: string } | null>(null)
  const [rejectionReason, setRejectionReason] = useState("")
  const [submitting, setSubmitting] = useState<string | null>(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await adminFetch(`/api/specialty-requests?status=${filter}`)
      const json = await res.json()
      if (json.success) setRequests(json.requests)
    } finally {
      setLoading(false)
    }
  }, [filter])

  useEffect(() => { load() }, [load])

  async function act(requestId: string, action: "approve" | "reject", reason?: string) {
    setSubmitting(requestId)
    try {
      const res = await adminFetch("/api/specialty-requests", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ requestId, action, rejectionReason: reason }),
      })
      const json = await res.json()
      if (json.success) {
        await load()
      } else {
        alert(json.error ?? "Erro ao processar solicitação")
      }
    } finally {
      setSubmitting(null)
      setRejectDialog(null)
      setRejectionReason("")
    }
  }

  const pending = requests.filter(r => r.status === "pending").length

  return (
    <PageWithBack backButtonLabel="Voltar">
      <div className="space-y-6">
        {/* Header */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
              <Layers className="h-5 w-5 text-primary" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground flex items-center gap-2">
                Aprovação de Especialidades
                {pending > 0 && (
                  <Badge variant="destructive">{pending}</Badge>
                )}
              </h1>
              <p className="text-sm text-muted-foreground">
                Revise e aprove alterações de especialidades solicitadas pelos prestadores
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Select value={filter} onValueChange={v => setFilter(v as typeof filter)}>
              <SelectTrigger className="w-40">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="pending">Pendentes</SelectItem>
                <SelectItem value="approved">Aprovadas</SelectItem>
                <SelectItem value="rejected">Rejeitadas</SelectItem>
                <SelectItem value="all">Todas</SelectItem>
              </SelectContent>
            </Select>
            <Button variant="outline" size="sm" onClick={load} disabled={loading}>
              <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
            </Button>
          </div>
        </div>

        {/* Lista */}
        {loading ? (
          <Card>
            <CardContent className="py-12 text-center text-sm text-muted-foreground">
              Carregando solicitações...
            </CardContent>
          </Card>
        ) : requests.length === 0 ? (
          <Card>
            <CardContent className="py-12 text-center text-sm text-muted-foreground">
              Nenhuma solicitação {filter !== "all" ? `com status "${STATUS_CONFIG[filter as keyof typeof STATUS_CONFIG]?.label ?? filter}"` : ""}.
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-4">
            {requests.map(req => {
              const cfg = STATUS_CONFIG[req.status]
              const Icon = cfg.icon
              const isProcessing = submitting === req.id

              return (
                <Card key={req.id}>
                  <CardHeader className="pb-3">
                    <div className="flex items-start justify-between gap-4">
                      <div>
                        <CardTitle className="text-base">{req.providerName}</CardTitle>
                        <CardDescription className="text-xs mt-1">
                          ID: {req.providerId} · Solicitado em {fmtDate(req.createdAt)}
                          {req.reviewedAt && ` · Revisado em ${fmtDate(req.reviewedAt)}`}
                        </CardDescription>
                      </div>
                      <Badge variant={cfg.variant} className="flex items-center gap-1 shrink-0">
                        <Icon className="h-3 w-3" />
                        {cfg.label}
                      </Badge>
                    </div>
                  </CardHeader>
                  <CardContent className="space-y-4">
                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                      <div>
                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                          Especialidades atuais
                        </p>
                        {req.currentServices.length === 0 ? (
                          <p className="text-sm text-muted-foreground italic">Nenhuma cadastrada</p>
                        ) : (
                          <div className="flex flex-wrap gap-1">
                            {req.currentServices.map(s => (
                              <Badge key={s} variant="outline" className="text-xs">{s}</Badge>
                            ))}
                          </div>
                        )}
                      </div>
                      <div>
                        <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-2">
                          Especialidades solicitadas
                        </p>
                        <div className="flex flex-wrap gap-1">
                          {req.requestedServices.map(s => {
                            const isNew = !req.currentServices.includes(s)
                            const isRemoved = req.currentServices.includes(s) && !req.requestedServices.includes(s)
                            return (
                              <Badge
                                key={s}
                                variant={isNew ? "default" : "outline"}
                                className={`text-xs ${isNew ? "bg-emerald-600" : ""}`}
                              >
                                {isNew ? "+ " : ""}{s}
                              </Badge>
                            )
                          })}
                          {req.currentServices
                            .filter(s => !req.requestedServices.includes(s))
                            .map(s => (
                              <Badge key={s} variant="destructive" className="text-xs opacity-60 line-through">
                                {s}
                              </Badge>
                            ))}
                        </div>
                      </div>
                    </div>

                    {req.rejectionReason && (
                      <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                        <span className="font-medium">Motivo da recusa: </span>
                        {req.rejectionReason}
                      </div>
                    )}

                    {req.status === "pending" && (
                      <div className="flex gap-2 pt-1">
                        <Button
                          size="sm"
                          onClick={() => act(req.id, "approve")}
                          disabled={isProcessing}
                          className="bg-emerald-600 hover:bg-emerald-700"
                        >
                          <CheckCircle className="mr-1.5 h-4 w-4" />
                          {isProcessing ? "Aprovando..." : "Aprovar"}
                        </Button>
                        <Button
                          size="sm"
                          variant="destructive"
                          onClick={() => {
                            setRejectDialog({ open: true, requestId: req.id })
                            setRejectionReason("")
                          }}
                          disabled={isProcessing}
                        >
                          <XCircle className="mr-1.5 h-4 w-4" />
                          Rejeitar
                        </Button>
                      </div>
                    )}
                  </CardContent>
                </Card>
              )
            })}
          </div>
        )}
      </div>

      {/* Dialog de rejeição */}
      <Dialog
        open={rejectDialog?.open ?? false}
        onOpenChange={open => !open && setRejectDialog(null)}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Rejeitar solicitação</DialogTitle>
            <DialogDescription>
              Informe o motivo da recusa (opcional). O prestador será notificado.
            </DialogDescription>
          </DialogHeader>
          <Textarea
            placeholder="Ex.: especialidade não corresponde aos documentos enviados"
            value={rejectionReason}
            onChange={e => setRejectionReason(e.target.value)}
            rows={3}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectDialog(null)}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              disabled={submitting !== null}
              onClick={() => {
                if (rejectDialog) act(rejectDialog.requestId, "reject", rejectionReason || undefined)
              }}
            >
              {submitting ? "Rejeitando..." : "Confirmar rejeição"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </PageWithBack>
  )
}
