"use client"

import Link from "next/link"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { ConversationsList } from "@/components/chat/conversations-list"
import { useChatActions, useChatConversations, useChatStats } from "@/hooks/use-chat"
import { useOperationalAlerts } from "@/hooks/use-operational-alerts"
import { ChatMessages } from "@/components/chat/chat-messages"
import { AdminActionsPanel } from "@/components/chat/admin-actions-panel"
import type { ChatFilter } from "@/types/chat"
import type { LegacyChatConversation } from "@/lib/services/chat-service"
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"
import {
  Activity,
  AlertTriangle,
  Bell,
  BellOff,
  Clock,
  Filter,
  Headphones,
  MessageSquare,
  RefreshCw,
  Radio,
  ShieldAlert,
  Siren,
  Sparkles,
} from "lucide-react"

function playSoftBeep() {
  try {
    const w = window as unknown as { webkitAudioContext?: typeof AudioContext; AudioContext: typeof AudioContext }
    const AudioContextClass = w.AudioContext || w.webkitAudioContext
    if (!AudioContextClass) return
    const ctx = new AudioContextClass()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.frequency.value = 784
    gain.gain.value = 0.06
    osc.start()
    osc.stop(ctx.currentTime + 0.12)
  } catch {
    /* ignore */
  }
}

export function ChatOperationsDashboard() {
  const [protocol, setProtocol] = useState("")
  const [provider, setProvider] = useState("")
  const [opStatus, setOpStatus] = useState("")
  const [priority, setPriority] = useState("all")
  const [status, setStatus] = useState("all")
  const [selectedConversation, setSelectedConversation] = useState<LegacyChatConversation | null>(null)
  const [browserAlerts, setBrowserAlerts] = useState(false)
  const seenAlertIds = useRef<Set<string>>(new Set())
  const bootstrapped = useRef(false)

  const filter = useMemo<ChatFilter>(
    () => ({
      protocolSearch: protocol.trim() || undefined,
      providerSearch: provider.trim() || undefined,
      serviceOperationalStatus: opStatus.trim() || undefined,
      priority: priority === "all" ? undefined : (priority as ChatFilter["priority"]),
      status: status === "all" ? undefined : (status as ChatFilter["status"]),
    }),
    [protocol, provider, opStatus, priority, status]
  )

  const { conversations, loading, error, refresh } = useChatConversations(filter)
  const { stats } = useChatStats()
  const { alerts, loading: alertsLoading, error: alertsError, openCount, criticalOpen } = useOperationalAlerts(120)
  const { acknowledgeOperationalAlert } = useChatActions()

  const pendingUnread = useMemo(
    () => conversations.reduce((sum, c) => sum + (c.unreadCount?.admin ?? 0), 0),
    [conversations]
  )

  const urgentActive = useMemo(
    () => conversations.filter((c) => c.priority === "urgent" && c.status === "active").length,
    [conversations]
  )

  useEffect(() => {
    if (!browserAlerts || alertsLoading) return
    if (!bootstrapped.current) {
      alerts.forEach((a) => seenAlertIds.current.add(a.id))
      bootstrapped.current = true
      return
    }
    const fresh = alerts.filter((a) => a.status === "open" && !seenAlertIds.current.has(a.id))
    for (const a of fresh) seenAlertIds.current.add(a.id)
    if (fresh.length === 0) return
    playSoftBeep()
    if (typeof window !== "undefined" && "Notification" in window && Notification.permission === "granted") {
      for (const a of fresh.slice(0, 3)) {
        // eslint-disable-next-line no-new
        new Notification(a.title, { body: a.detail.slice(0, 180), tag: a.id })
      }
    }
  }, [alerts, alertsLoading, browserAlerts])

  useEffect(() => {
    if (conversations.length === 0) {
      setSelectedConversation(null)
      return
    }

    setSelectedConversation((current) => {
      if (!current) {
        return conversations[0]
      }
      return conversations.find((conversation) => conversation.id === current.id) || conversations[0]
    })
  }, [conversations])

  const requestNotificationPermission = useCallback(async () => {
    if (typeof window === "undefined" || !("Notification" in window)) return
    const result = await Notification.requestPermission()
    setBrowserAlerts(result === "granted")
  }, [])

  const handleAckAlert = async (id: string) => {
    await acknowledgeOperationalAlert(id, "admin-dashboard")
    seenAlertIds.current.add(id)
  }

  const hasActiveFilters = Boolean(protocol || provider || opStatus || priority !== "all" || status !== "all")

  return (
    <div className="space-y-6">
      <Card className="overflow-hidden border-border/60 bg-[radial-gradient(circle_at_top_left,_rgba(249,115,22,0.14),_transparent_28%),linear-gradient(135deg,rgba(255,255,255,0.98),rgba(255,247,237,0.95))] dark:bg-[radial-gradient(circle_at_top_left,_rgba(251,146,60,0.08),_transparent_28%),linear-gradient(135deg,rgba(24,26,30,0.98),rgba(31,18,8,0.95))]">
        <CardContent className="flex flex-col gap-6 p-5 sm:p-6 xl:flex-row xl:items-end xl:justify-between">
          <div className="space-y-4">
            <div className="inline-flex items-center gap-2 rounded-full border border-orange-200 dark:border-orange-800 bg-white/80 dark:bg-card/70 px-3 py-1 text-xs font-medium text-orange-800 dark:text-orange-300 shadow-sm">
              <Sparkles className="h-3.5 w-3.5" />
              Operação em tempo real
            </div>
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-tight text-foreground">Central operacional de chat</h1>
              <p className="max-w-4xl text-sm leading-6 text-muted-foreground sm:text-base">
                Monitore conversas críticas, priorize incidentes e atue diretamente no atendimento sem sair da mesma tela.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Badge variant="outline" className="bg-card/70 dark:bg-card/70">Fila operacional</Badge>
              <Badge variant="outline" className="bg-card/70 dark:bg-card/70">{pendingUnread} não lidas</Badge>
              <Badge variant="outline" className="bg-card/70 dark:bg-card/70">{openCount} alertas abertos</Badge>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 xl:min-w-[420px]">
            <div className="rounded-2xl border border-red-200 dark:border-red-900/60 bg-red-50/80 dark:bg-red-950/30 p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-red-700 dark:text-red-400">Prioridade máxima</p>
                  <p className="mt-2 text-3xl font-bold text-red-600 dark:text-red-400">{urgentActive}</p>
                  <p className="mt-1 text-sm text-red-700 dark:text-red-400/70">conversas urgentes em andamento</p>
                </div>
                <Siren className="h-5 w-5 text-red-500 dark:text-red-400" />
              </div>
            </div>
            <div className="rounded-2xl border border-blue-200 dark:border-blue-900/60 bg-blue-50/80 dark:bg-blue-950/30 p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-blue-700 dark:text-blue-400">Equipe</p>
                  <p className="mt-2 text-3xl font-bold text-blue-600 dark:text-blue-400">{stats?.activeConversations ?? "—"}</p>
                  <p className="mt-1 text-sm text-blue-700 dark:text-blue-400/70">threads ativos monitorados</p>
                </div>
                <Headphones className="h-5 w-5 text-blue-500 dark:text-blue-400" />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="flex flex-wrap gap-2">
        <Button variant={browserAlerts ? "secondary" : "default"} size="sm" onClick={requestNotificationPermission} className="gap-2">
          {browserAlerts ? <Bell className="h-4 w-4" /> : <BellOff className="h-4 w-4" />}
          {browserAlerts ? "Notificações ativas" : "Ativar notificações"}
        </Button>
        <Button variant="outline" size="sm" onClick={() => refresh()} className="gap-2">
          <RefreshCw className="h-4 w-4" />
          Atualizar fila
        </Button>
        <Button asChild variant="outline" size="sm">
          <Link href="/dashboard/controle/chat">Monitor clássico</Link>
        </Button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 xl:grid-cols-6">
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <MessageSquare className="h-4 w-4" />
              Conversas (filtro)
            </CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-bold">{loading ? "…" : conversations.length}</CardContent>
        </Card>
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Activity className="h-4 w-4" />
              Ativas (todas)
            </CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-bold">{stats?.activeConversations ?? "—"}</CardContent>
        </Card>
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Clock className="h-4 w-4" />
              Não lidas (admin)
            </CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-bold text-orange-600">{pendingUnread}</CardContent>
        </Card>
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <AlertTriangle className="h-4 w-4 text-red-500" />
              Urgentes ativas
            </CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-bold text-red-600">{urgentActive}</CardContent>
        </Card>
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <Radio className="h-4 w-4" />
              Alertas abertos
            </CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-bold">{alertsLoading ? "…" : openCount}</CardContent>
        </Card>
        <Card className="border-border/70 shadow-sm">
          <CardHeader className="pb-2">
            <CardTitle className="flex items-center gap-2 text-sm font-medium text-muted-foreground">
              <ShieldAlert className="h-4 w-4 text-red-600" />
              Críticos abertos
            </CardTitle>
          </CardHeader>
          <CardContent className="text-2xl font-bold text-red-700 dark:text-red-400">{alertsLoading ? "…" : criticalOpen}</CardContent>
        </Card>
      </div>

      <div className="grid gap-6 2xl:grid-cols-[380px_minmax(0,1fr)]">
        <div className="space-y-6">
          <Card className="overflow-hidden border-border/70 shadow-sm">
            <CardHeader className="border-b pb-4">
              <CardTitle className="flex items-center justify-between gap-3 text-lg">
                <span className="flex items-center gap-2">
                  <Filter className="h-5 w-5" />
                  Filtros operacionais
                </span>
                {hasActiveFilters ? (
                  <Badge variant="secondary">Filtros ativos</Badge>
                ) : (
                  <Badge variant="outline">Fila completa</Badge>
                )}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4 pt-4">
              <div className="space-y-1">
                <p className="text-xs font-medium text-muted-foreground">Protocolo / pedido</p>
                <Input value={protocol} onChange={(e) => setProtocol(e.target.value)} placeholder="Ex.: ABC12345" />
              </div>
              <div className="space-y-1">
                <p className="text-xs font-medium text-muted-foreground">Prestador</p>
                <Input value={provider} onChange={(e) => setProvider(e.target.value)} placeholder="Nome ou telefone" />
              </div>
              <div className="space-y-1">
                <p className="text-xs font-medium text-muted-foreground">Status operacional</p>
                <Input value={opStatus} onChange={(e) => setOpStatus(e.target.value)} placeholder="ex.: em_atendimento" />
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1">
                  <p className="text-xs font-medium text-muted-foreground">Prioridade</p>
                  <Select value={priority} onValueChange={setPriority}>
                    <SelectTrigger>
                      <SelectValue placeholder="Prioridade" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todas</SelectItem>
                      <SelectItem value="urgent">Urgente</SelectItem>
                      <SelectItem value="high">Alta</SelectItem>
                      <SelectItem value="medium">Média</SelectItem>
                      <SelectItem value="low">Baixa</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <p className="text-xs font-medium text-muted-foreground">Status conversa</p>
                  <Select value={status} onValueChange={setStatus}>
                    <SelectTrigger>
                      <SelectValue placeholder="Status" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">Todos</SelectItem>
                      <SelectItem value="active">Ativa</SelectItem>
                      <SelectItem value="closed">Encerrada</SelectItem>
                      <SelectItem value="archived">Arquivada</SelectItem>
                      <SelectItem value="blocked">Bloqueada</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="flex flex-wrap gap-2 pt-1">
                <Button variant="outline" size="sm" onClick={() => refresh()} className="gap-2">
                  <RefreshCw className="h-4 w-4" />
                  Aplicar / atualizar
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    setProtocol("")
                    setProvider("")
                    setOpStatus("")
                    setPriority("all")
                    setStatus("all")
                  }}
                  disabled={!hasActiveFilters}
                >
                  Limpar filtros
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card className="overflow-hidden border-border/70 shadow-sm">
            <CardHeader className="border-b">
              <CardTitle className="text-lg">Alertas operacionais</CardTitle>
              <p className="text-sm text-muted-foreground">
                Incidentes em tempo real com foco em triagem rápida e reconhecimento operacional.
              </p>
            </CardHeader>
            <CardContent className="max-h-[520px] space-y-3 overflow-y-auto pt-4">
              {alertsLoading ? <p className="text-sm text-muted-foreground">Carregando…</p> : null}
              {alertsError ? <p className="text-sm text-destructive">{alertsError}</p> : null}
              {alerts.length === 0 && !alertsLoading ? (
                <div className="rounded-2xl border border-dashed p-6 text-center">
                  <ShieldAlert className="mx-auto mb-3 h-10 w-10 text-muted-foreground/40" />
                  <p className="text-sm font-medium text-foreground">Nenhum alerta aberto</p>
                  <p className="mt-1 text-sm text-muted-foreground">Quando um operador sinalizar risco no chat, ele aparece aqui.</p>
                </div>
              ) : null}
              {alerts.map((a) => (
                <div key={a.id} className="rounded-2xl border bg-card p-4 text-sm shadow-sm transition-shadow hover:shadow-md">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant={a.severity === "critical" ? "destructive" : "secondary"}>{a.severity}</Badge>
                      <Badge variant="outline">{a.kind}</Badge>
                      <Badge variant={a.status === "open" ? "default" : "outline"}>{a.status}</Badge>
                    </div>
                    <span className="text-xs text-muted-foreground">
                      {formatDistanceToNow(a.createdAt, { addSuffix: true, locale: ptBR })}
                    </span>
                  </div>
                  <p className="mt-3 font-semibold text-foreground">{a.title}</p>
                  <p className="mt-1 leading-6 text-muted-foreground">{a.detail}</p>
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button asChild size="sm" variant="outline">
                      <Link href={`/dashboard/controle/chat?orderId=${encodeURIComponent(a.orderId)}`}>Abrir chat</Link>
                    </Button>
                    {a.orderId ? (
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => {
                          const target = conversations.find((conversation) => conversation.orderId === a.orderId)
                          if (target) {
                            setSelectedConversation(target)
                          }
                        }}
                      >
                        Focar conversa
                      </Button>
                    ) : null}
                    {a.status === "open" ? (
                      <Button size="sm" onClick={() => handleAckAlert(a.id)}>
                        Reconhecer
                      </Button>
                    ) : null}
                  </div>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>

        <div className="space-y-6">
        <Card className="overflow-hidden border-border/70 shadow-sm">
          <CardHeader className="border-b">
            <CardTitle className="flex items-center justify-between gap-2 text-lg">
              <span className="flex items-center gap-2">
                <Headphones className="h-5 w-5" />
                Conversas monitoradas
              </span>
              <Badge variant="outline">{loading ? "Atualizando…" : `${conversations.length} itens`}</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="h-[560px] p-0">
            {error ? <p className="p-4 text-sm text-destructive">{error}</p> : null}
            <ConversationsList
              onSelectConversation={setSelectedConversation}
              selectedConversationId={selectedConversation?.id}
              onConversationsLoaded={() => {}}
              conversationsOverride={conversations}
              loadingOverride={loading}
            />
          </CardContent>
        </Card>

          {selectedConversation ? (
            <>
              <Card className="border-border/70 shadow-sm">
                <CardHeader className="border-b">
                  <CardTitle className="text-base">Contexto da seleção</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-3 pt-4 md:grid-cols-2 xl:grid-cols-4">
                  <div className="rounded-xl border bg-muted/20 p-3 text-sm">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Cliente</p>
                    <p className="mt-1 font-medium text-foreground">{selectedConversation.clientName}</p>
                    <p>
                      <span className="text-muted-foreground">Telefone:</span> {selectedConversation.clientPhone || "—"}
                    </p>
                  </div>
                  <div className="rounded-xl border bg-muted/20 p-3 text-sm">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Prestador</p>
                    {selectedConversation.providerName ? (
                      <>
                        <p className="mt-1 font-medium text-foreground">{selectedConversation.providerName}</p>
                        <p className="text-muted-foreground">{selectedConversation.providerPhone || "Sem telefone"}</p>
                      </>
                    ) : (
                      <p className="mt-1 text-muted-foreground">Não associado neste pedido.</p>
                    )}
                  </div>
                  <div className="rounded-xl border bg-muted/20 p-3 text-sm">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Pedido</p>
                    <p className="mt-1 font-medium text-foreground">{selectedConversation.orderProtocol || selectedConversation.orderId}</p>
                    <p>
                      <span className="text-muted-foreground">Prioridade:</span> {selectedConversation.priority}
                    </p>
                  </div>
                  <div className="rounded-xl border bg-muted/20 p-3 text-sm">
                    <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Operação</p>
                    <p className="mt-1 font-medium text-foreground">{selectedConversation.serviceOperationalStatus || "Sem status operacional"}</p>
                    <p>
                      <span className="text-muted-foreground">Status:</span> {selectedConversation.status}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-2 md:col-span-2 xl:col-span-4">
                    <Button asChild className="w-full sm:w-auto">
                      <Link
                        href={`/dashboard/controle/chat?orderId=${encodeURIComponent(selectedConversation.orderId)}&protocolo=${encodeURIComponent(
                          String(selectedConversation.orderProtocol || selectedConversation.orderId)
                        )}`}
                      >
                        Abrir thread completo
                      </Link>
                    </Button>
                    <Button type="button" variant="outline" onClick={() => refresh()} className="w-full sm:w-auto">
                      Sincronizar lista
                    </Button>
                  </div>
                </CardContent>
              </Card>

              <div className="grid gap-4 2xl:grid-cols-[minmax(0,1fr)_340px]">
                <div className="min-w-0 h-[680px]">
                  <ChatMessages conversation={selectedConversation} />
                </div>
                <div className="min-w-0">
                  <AdminActionsPanel conversation={selectedConversation} onUpdate={() => refresh()} />
                </div>
              </div>
            </>
          ) : (
            <Card className="border-dashed border-border/80">
              <CardContent className="flex h-[420px] flex-col items-center justify-center text-center">
                <MessageSquare className="mb-4 h-12 w-12 text-muted-foreground/35" />
                <p className="text-base font-medium text-foreground">Selecione uma conversa</p>
                <p className="mt-1 max-w-md text-sm text-muted-foreground">
                  A fila, o contexto operacional, o thread e as ações administrativas aparecem aqui assim que você escolher um atendimento.
                </p>
              </CardContent>
            </Card>
          )}
        </div>
      </div>
    </div>
  )
}
