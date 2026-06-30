"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import {
  RefreshCw,
  Loader2,
  RotateCcw,
  AlertCircle,
  CheckCircle2,
  Clock,
  Search,
} from "lucide-react"

import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import { useToast } from "@/hooks/use-toast"
import { adminFetch } from "@/lib/admin-api"
import { usePermissions } from "@/hooks/use-permissions"

interface PendingRefund {
  id: string
  protocol: string | null
  clientId: string | null
  clientName: string
  serviceName: string
  amount: number
  paymentStatus: string | null
  status: string | null
  cancelledBy: string | null
  cancellationReason: string | null
  refundStatus: string | null
  refundReason: string | null
  refundPhotos: string[]
  refundRequestedAtMs: number
  hasGatewayTransaction: boolean
}

const formatCurrency = (v: number) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(v || 0)

const formatWhen = (ms: number) => {
  if (!ms) return "—"
  const diff = Date.now() - ms
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  const rel = days > 0 ? `há ${days}d` : hours > 0 ? `há ${hours}h` : "agora"
  return `${new Date(ms).toLocaleString("pt-BR")} (${rel})`
}

export default function ReembolsosPage() {
  const { hasPermission } = usePermissions()
  const canOperateFinance = hasPermission("operarFinanceiro")
  const { toast } = useToast()

  const [items, setItems] = useState<PendingRefund[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState("")

  const [selected, setSelected] = useState<PendingRefund | null>(null)
  const [reason, setReason] = useState("")
  const [processing, setProcessing] = useState(false)

  const [rejecting, setRejecting] = useState<PendingRefund | null>(null)
  const [rejectReason, setRejectReason] = useState("")
  const [processingReject, setProcessingReject] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const res = await adminFetch("/api/orders/refunds/pending")
      const data = await res.json()
      if (!res.ok || !data.success) {
        throw new Error(data.error || "Falha ao carregar reembolsos pendentes")
      }
      setItems(data.items as PendingRefund[])
    } catch (e) {
      setError(e instanceof Error ? e.message : "Erro desconhecido")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return items
    return items.filter(
      (i) =>
        i.clientName.toLowerCase().includes(q) ||
        i.serviceName.toLowerCase().includes(q) ||
        (i.protocol ?? "").toLowerCase().includes(q),
    )
  }, [items, search])

  const totalPending = useMemo(
    () => items.reduce((acc, i) => acc + (i.amount || 0), 0),
    [items],
  )

  const processRefund = async () => {
    if (!selected) return
    setProcessing(true)
    try {
      const res = await adminFetch(`/api/orders/${selected.id}/refund`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: reason.trim() || "Reembolso de pedido cancelado" }),
      })
      const data = await res.json()
      if (!res.ok || !data.success) {
        throw new Error(data.error || "Falha ao processar o reembolso")
      }
      toast({
        title: "Reembolso processado",
        description: `${selected.clientName} — ${formatCurrency(selected.amount)} estornado na Pagar.me.`,
      })
      setSelected(null)
      setReason("")
      await load()
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Erro no reembolso",
        description: e instanceof Error ? e.message : "Erro desconhecido",
      })
    } finally {
      setProcessing(false)
    }
  }

  const rejectRefund = async () => {
    if (!rejecting) return
    if (rejectReason.trim().length < 3) return
    setProcessingReject(true)
    try {
      const res = await adminFetch(`/api/orders/${rejecting.id}/refund/reject`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: rejectReason.trim() }),
      })
      const data = await res.json()
      if (!res.ok || !data.success) {
        throw new Error(data.error || "Falha ao recusar a solicitação")
      }
      toast({
        title: "Solicitação recusada",
        description: `${rejecting.clientName} foi notificado do motivo no app.`,
      })
      setRejecting(null)
      setRejectReason("")
      await load()
    } catch (e) {
      toast({
        variant: "destructive",
        title: "Erro ao recusar",
        description: e instanceof Error ? e.message : "Erro desconhecido",
      })
    } finally {
      setProcessingReject(false)
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground flex items-center gap-2">
            <RotateCcw className="h-6 w-6 text-primary" />
            Reembolsos pendentes
          </h1>
          <p className="text-sm text-muted-foreground">
            Solicitações de reembolso dos clientes (motivo + fotos) e pedidos aguardando estorno.
            Aprove para estornar na Pagar.me ou recuse informando o motivo.
          </p>
        </div>
        <Button variant="outline" onClick={() => void load()} disabled={loading}>
          <RefreshCw className={`h-4 w-4 mr-2 ${loading ? "animate-spin" : ""}`} />
          Atualizar
        </Button>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <Card>
          <CardContent className="p-5 flex items-center gap-4">
            <div className="h-11 w-11 rounded-xl bg-amber-500/15 flex items-center justify-center">
              <Clock className="h-5 w-5 text-amber-600" />
            </div>
            <div>
              <p className="text-2xl font-bold">{items.length}</p>
              <p className="text-sm text-muted-foreground">Aguardando estorno</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5 flex items-center gap-4">
            <div className="h-11 w-11 rounded-xl bg-primary/15 flex items-center justify-center">
              <RotateCcw className="h-5 w-5 text-primary" />
            </div>
            <div>
              <p className="text-2xl font-bold">{formatCurrency(totalPending)}</p>
              <p className="text-sm text-muted-foreground">Valor total pendente</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="relative max-w-sm">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Buscar por cliente, serviço ou protocolo"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
        />
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-muted-foreground">
          <Loader2 className="h-6 w-6 animate-spin mr-2" /> Carregando…
        </div>
      ) : error ? (
        <Card>
          <CardContent className="p-8 text-center">
            <AlertCircle className="h-8 w-8 text-destructive mx-auto mb-2" />
            <p className="text-sm text-muted-foreground">{error}</p>
            <Button onClick={() => void load()} className="mt-4" variant="outline">
              Tentar novamente
            </Button>
          </CardContent>
        </Card>
      ) : filtered.length === 0 ? (
        <Card>
          <CardContent className="p-10 text-center">
            <CheckCircle2 className="h-10 w-10 text-emerald-500 mx-auto mb-3" />
            <p className="font-medium">Nenhum reembolso pendente</p>
            <p className="text-sm text-muted-foreground">
              Todos os cancelamentos pagos já foram estornados.
            </p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {filtered.map((r) => (
            <Card key={r.id}>
              <CardContent className="p-4 flex flex-col gap-3">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2 flex-wrap">
                      <span className="font-semibold truncate">{r.clientName}</span>
                      {r.protocol && (
                        <Badge variant="outline" className="text-xs">#{r.protocol}</Badge>
                      )}
                      {r.refundStatus === "requested" ? (
                        <Badge className="text-xs bg-amber-500/15 text-amber-700 hover:bg-amber-500/15">
                          Solicitação do cliente
                        </Badge>
                      ) : (
                        <Badge variant="secondary" className="text-xs">Aprovado · aguardando estorno</Badge>
                      )}
                      <Badge variant="secondary" className="text-xs capitalize">
                        {r.status === "expired" ? "Expirado" : "Cancelado"}
                        {r.cancelledBy ? ` · ${r.cancelledBy === "client" ? "cliente" : r.cancelledBy}` : ""}
                      </Badge>
                    </div>
                    <p className="text-sm text-muted-foreground truncate">{r.serviceName}</p>
                    <p className="text-xs text-muted-foreground">Solicitado {formatWhen(r.refundRequestedAtMs)}</p>
                  </div>
                  <span className="text-lg font-bold shrink-0">{formatCurrency(r.amount)}</span>
                </div>

                {/* Motivo descrito pelo cliente */}
                {(r.refundReason || r.cancellationReason) && (
                  <div className="rounded-lg bg-muted/50 p-3">
                    <p className="text-xs font-medium text-muted-foreground mb-0.5">Motivo do cliente</p>
                    <p className="text-sm">{r.refundReason || r.cancellationReason}</p>
                  </div>
                )}

                {/* Fotos anexadas pelo cliente */}
                {r.refundPhotos.length > 0 && (
                  <div className="flex gap-2 flex-wrap">
                    {r.refundPhotos.map((url, i) => (
                      <a key={i} href={url} target="_blank" rel="noopener noreferrer" title="Abrir foto">
                        {/* eslint-disable-next-line @next/next/no-img-element */}
                        <img
                          src={url}
                          alt={`Foto ${i + 1} do reembolso`}
                          className="h-20 w-20 rounded-md object-cover border hover:opacity-80 transition"
                        />
                      </a>
                    ))}
                  </div>
                )}

                {!r.hasGatewayTransaction && (
                  <p className="text-xs text-destructive flex items-center gap-1">
                    <AlertCircle className="h-3 w-3" /> Sem transação Pagar.me — estorno manual necessário
                  </p>
                )}

                <div className="flex items-center justify-end gap-2">
                  <Button
                    variant="outline"
                    onClick={() => {
                      setRejecting(r)
                      setRejectReason("")
                    }}
                    disabled={!canOperateFinance}
                    title={!canOperateFinance ? "Requer permissão de operar financeiro" : undefined}
                  >
                    Recusar
                  </Button>
                  <Button
                    onClick={() => {
                      setSelected(r)
                      setReason(r.refundReason || r.cancellationReason || "")
                    }}
                    disabled={!canOperateFinance || !r.hasGatewayTransaction}
                    title={
                      !canOperateFinance
                        ? "Requer permissão de operar financeiro"
                        : !r.hasGatewayTransaction
                          ? "Pedido sem transação Pagar.me"
                          : undefined
                    }
                  >
                    <RotateCcw className="h-4 w-4 mr-2" />
                    Aprovar e estornar
                  </Button>
                </div>
              </CardContent>
            </Card>
          ))}
        </div>
      )}

      {/* Diálogo de confirmação */}
      <Dialog open={!!selected} onOpenChange={(o) => !o && !processing && setSelected(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Confirmar reembolso</DialogTitle>
            <DialogDescription>
              O valor será estornado na Pagar.me pelo mesmo meio de pagamento do cliente.
              Esta ação não pode ser desfeita.
            </DialogDescription>
          </DialogHeader>
          {selected && (
            <div className="space-y-3">
              <div className="rounded-lg border p-3 text-sm space-y-1">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Cliente</span>
                  <span className="font-medium">{selected.clientName}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Serviço</span>
                  <span className="font-medium">{selected.serviceName}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Valor</span>
                  <span className="font-bold">{formatCurrency(selected.amount)}</span>
                </div>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="reason">Motivo (opcional)</Label>
                <Textarea
                  id="reason"
                  value={reason}
                  onChange={(e) => setReason(e.target.value)}
                  placeholder="Ex.: Pedido cancelado pelo cliente antes do atendimento"
                  rows={2}
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setSelected(null)} disabled={processing}>
              Cancelar
            </Button>
            <Button onClick={() => void processRefund()} disabled={processing}>
              {processing ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" /> Processando…
                </>
              ) : (
                "Confirmar reembolso"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Diálogo de recusa */}
      <Dialog open={!!rejecting} onOpenChange={(o) => !o && !processingReject && setRejecting(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Recusar solicitação de reembolso</DialogTitle>
            <DialogDescription>
              Informe o motivo da recusa. Ele será enviado ao cliente e aparecerá dentro do
              pedido, no app.
            </DialogDescription>
          </DialogHeader>
          {rejecting && (
            <div className="space-y-3">
              <div className="rounded-lg border p-3 text-sm">
                <span className="text-muted-foreground">Cliente: </span>
                <span className="font-medium">{rejecting.clientName}</span>
                <span className="text-muted-foreground"> · {rejecting.serviceName}</span>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="rejectReason">Motivo da recusa *</Label>
                <Textarea
                  id="rejectReason"
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  placeholder="Ex.: O serviço foi executado conforme combinado; não há motivo para estorno."
                  rows={3}
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejecting(null)} disabled={processingReject}>
              Cancelar
            </Button>
            <Button
              variant="destructive"
              onClick={() => void rejectRefund()}
              disabled={processingReject || rejectReason.trim().length < 3}
            >
              {processingReject ? (
                <>
                  <Loader2 className="h-4 w-4 mr-2 animate-spin" /> Recusando…
                </>
              ) : (
                "Confirmar recusa"
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
