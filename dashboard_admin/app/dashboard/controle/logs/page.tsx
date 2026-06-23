"use client"

import { useState, useEffect, useCallback } from "react"
import { Card, CardContent } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { RefreshCw, ShieldCheck, Clock } from "lucide-react"
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"
import { adminFetch } from "@/lib/admin-api"

const ACTION_CONFIG: Record<string, { label: string; color: string }> = {
  verify_provider:    { label: "Verificação prestador",  color: "bg-amber-100 text-amber-800" },
  block_user:         { label: "Bloqueio usuário",        color: "bg-red-100 text-red-800" },
  unblock_user:       { label: "Desbloqueio usuário",     color: "bg-emerald-100 text-emerald-800" },
  cancel_order:       { label: "Cancelamento pedido",     color: "bg-red-100 text-red-800" },
  redirect_order:     { label: "Redirecionamento OS",     color: "bg-purple-100 text-purple-800" },
  send_notification:  { label: "Notificação enviada",     color: "bg-blue-100 text-blue-800" },
}

interface LogEntry {
  id: string
  action: string
  targetId: string
  targetType: string
  adminId?: string
  payload?: Record<string, unknown>
  note?: string
  createdAt?: string
}

export default function AuditLogsPage() {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [loading, setLoading] = useState(true)
  const [actionFilter, setActionFilter] = useState("all")
  const [typeFilter, setTypeFilter] = useState("all")

  const fetchLogs = useCallback(async () => {
    setLoading(true)
    try {
      const params = new URLSearchParams({ limit: "100" })
      if (actionFilter !== "all") params.set("action", actionFilter)
      if (typeFilter !== "all") params.set("targetType", typeFilter)
      const res = await adminFetch(`/api/admin-logs?${params}`)
      const data = await res.json()
      if (data.success) setLogs(data.logs ?? [])
    } finally {
      setLoading(false)
    }
  }, [actionFilter, typeFilter])

  useEffect(() => { fetchLogs() }, [fetchLogs])

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="h-9 w-9 rounded-lg bg-slate-100 flex items-center justify-center">
            <ShieldCheck className="h-5 w-5 text-slate-600" />
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight">Logs de Auditoria</h1>
            <p className="text-sm text-muted-foreground">{logs.length} ação(ões) registrada(s)</p>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={fetchLogs} disabled={loading} className="gap-2">
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Atualizar
        </Button>
      </div>

      <div className="flex gap-3 flex-wrap">
        <Select value={actionFilter} onValueChange={v => { setActionFilter(v) }}>
          <SelectTrigger className="w-52"><SelectValue placeholder="Filtrar ação" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Todas as ações</SelectItem>
            {Object.entries(ACTION_CONFIG).map(([k, v]) => (
              <SelectItem key={k} value={k}>{v.label}</SelectItem>
            ))}
          </SelectContent>
        </Select>

        <Select value={typeFilter} onValueChange={v => { setTypeFilter(v) }}>
          <SelectTrigger className="w-44"><SelectValue placeholder="Tipo" /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Todos os tipos</SelectItem>
            <SelectItem value="provider">Prestador</SelectItem>
            <SelectItem value="user">Usuário</SelectItem>
            <SelectItem value="order">Pedido</SelectItem>
          </SelectContent>
        </Select>
      </div>

      <Card>
        <CardContent className="p-0">
          {loading ? (
            <div className="flex items-center justify-center h-40">
              <RefreshCw className="h-6 w-6 animate-spin text-muted-foreground" />
            </div>
          ) : logs.length === 0 ? (
            <div className="flex flex-col items-center justify-center h-40 gap-2">
              <ShieldCheck className="h-8 w-8 text-muted-foreground" />
              <p className="text-sm text-muted-foreground">Nenhuma ação registrada</p>
            </div>
          ) : (
            <div className="divide-y">
              {logs.map(log => {
                const cfg = ACTION_CONFIG[log.action] ?? { label: log.action, color: "bg-muted text-muted-foreground" }
                const ago = log.createdAt
                  ? formatDistanceToNow(new Date(log.createdAt), { addSuffix: true, locale: ptBR })
                  : "—"

                // Resumo do payload em texto
                const payloadSummary = log.payload
                  ? Object.entries(log.payload)
                      .filter(([, v]) => v !== null && v !== undefined)
                      .map(([k, v]) => `${k}: ${v}`)
                      .join(" · ")
                  : ""

                return (
                  <div key={log.id} className="flex items-start gap-4 px-4 py-3 hover:bg-muted/30">
                    <div className="flex-1 min-w-0 space-y-0.5">
                      <div className="flex items-center gap-2 flex-wrap">
                        <Badge className={`text-xs ${cfg.color}`}>{cfg.label}</Badge>
                        <span className="text-xs text-muted-foreground font-mono">
                          {log.targetType}/{log.targetId.slice(0, 12)}
                        </span>
                      </div>
                      {payloadSummary && (
                        <p className="text-xs text-muted-foreground">{payloadSummary}</p>
                      )}
                      {log.note && (
                        <p className="text-xs text-muted-foreground italic">{log.note}</p>
                      )}
                    </div>
                    <div className="flex items-center gap-1 text-xs text-muted-foreground shrink-0">
                      <Clock className="h-3 w-3" />
                      {ago}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
