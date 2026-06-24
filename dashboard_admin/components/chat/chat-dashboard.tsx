"use client"

import { useCallback, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { LegacyChatConversation } from "@/lib/services/chat-service"
import { AlertTriangle, Clock, MessageCircle, RefreshCw, Sparkles, Users } from "lucide-react"
import { useChatStats } from "@/hooks/use-chat"
import { ChatStatsCards } from "./chat-stats-cards"
import { ConversationsList } from "./conversations-list"
import { ChatMessages } from "./chat-messages"
import { AdminActionsPanel } from "./admin-actions-panel"

interface ChatDashboardProps {
  initialProtocolo?: string | null
  initialServicoId?: string | null
  initialOrderId?: string | null
}

export function ChatDashboard({ initialProtocolo, initialServicoId, initialOrderId }: ChatDashboardProps) {
  const [selectedConversation, setSelectedConversation] = useState<LegacyChatConversation | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const { stats } = useChatStats()

  const handleSelectConversation = useCallback((conversation: LegacyChatConversation) => {
    setSelectedConversation(conversation)
  }, [])

  const handleConversationsLoaded = useCallback(
    (conversations: LegacyChatConversation[]) => {
      if (conversations.length === 0) {
        setSelectedConversation(null)
        return
      }

      if (selectedConversation) {
        const updatedConversation = conversations.find((conversation) => conversation.id === selectedConversation.id)
        if (updatedConversation) {
          setSelectedConversation(updatedConversation)
          return
        }
      }

      const targetConversation =
        conversations.find(
          (conversation) =>
            conversation.orderProtocol === initialProtocolo ||
            conversation.orderId === initialServicoId ||
            conversation.orderId === initialOrderId
        ) || conversations[0]

      setSelectedConversation(targetConversation)
    },
    [initialOrderId, initialProtocolo, initialServicoId, selectedConversation]
  )

  const urgentConversations = stats?.conversationsByPriority.urgent ?? 0
  const unreadMessages = stats?.unreadMessages ?? 0
  const activeConversations = stats?.activeConversations ?? 0

  return (
    <div className="space-y-6">
      {/* Hero Card */}
      <Card className="overflow-hidden border-border/60 bg-[radial-gradient(circle_at_top_left,_rgba(249,115,22,0.14),_transparent_30%),linear-gradient(135deg,rgba(255,255,255,0.98),rgba(255,247,237,0.94))] dark:bg-[radial-gradient(circle_at_top_left,_rgba(251,146,60,0.08),_transparent_30%),linear-gradient(135deg,rgba(24,26,30,0.98),rgba(31,18,8,0.94))]">
        <CardContent className="flex flex-col gap-6 p-5 sm:p-6 xl:flex-row xl:items-end xl:justify-between">
          <div className="space-y-4">
            <div className="inline-flex items-center gap-2 rounded-full border border-orange-200 dark:border-orange-800 bg-white/85 dark:bg-card/70 px-3 py-1 text-xs font-medium text-orange-800 dark:text-orange-300 shadow-sm">
              <Sparkles className="h-3.5 w-3.5" />
              Central de monitoramento
            </div>
            <div className="space-y-2">
              <h1 className="text-3xl font-bold tracking-tight text-foreground">Monitoramento de Chat</h1>
              <p className="max-w-4xl text-sm leading-6 text-muted-foreground sm:text-base">
                Acompanhe conversas ativas, priorize urgências e execute ações administrativas sem perder o contexto do pedido.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Badge variant="outline" className="bg-card/70 dark:bg-card/70">{activeConversations} ativas</Badge>
              <Badge variant="outline" className="bg-card/70 dark:bg-card/70">{unreadMessages} não lidas</Badge>
              <Badge variant="outline" className="bg-card/70 dark:bg-card/70">{urgentConversations} urgentes</Badge>
            </div>
          </div>

          {/* Stats mini-cards */}
          <div className="grid gap-3 sm:grid-cols-2 xl:min-w-[430px]">
            <div className="rounded-2xl border border-red-200 dark:border-red-900/60 bg-red-50/80 dark:bg-red-950/30 p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-red-700 dark:text-red-400">Urgência</p>
                  <p className="mt-2 text-3xl font-bold text-red-600 dark:text-red-400">{urgentConversations}</p>
                  <p className="mt-1 text-sm text-red-700 dark:text-red-400/70">conversas exigindo reação imediata</p>
                </div>
                <AlertTriangle className="h-5 w-5 text-red-500 dark:text-red-400" />
              </div>
            </div>
            <div className="rounded-2xl border border-blue-200 dark:border-blue-900/60 bg-blue-50/80 dark:bg-blue-950/30 p-4 shadow-sm">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-blue-700 dark:text-blue-400">Fila ativa</p>
                  <p className="mt-2 text-3xl font-bold text-blue-600 dark:text-blue-400">{activeConversations}</p>
                  <p className="mt-1 text-sm text-blue-700 dark:text-blue-400/70">threads abertos no monitor</p>
                </div>
                <Users className="h-5 w-5 text-blue-500 dark:text-blue-400" />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Action bar */}
      <div className="flex flex-wrap gap-2">
        <Button variant="outline" size="sm" onClick={() => setRefreshKey((value) => value + 1)} className="gap-2">
          <RefreshCw className="h-4 w-4" />
          Atualizar visão
        </Button>
        {selectedConversation ? (
          <Badge variant="secondary" className="px-3 py-1">
            Selecionada: {selectedConversation.clientName}
          </Badge>
        ) : (
          <Badge variant="outline" className="px-3 py-1">Nenhuma conversa selecionada</Badge>
        )}
      </div>

      {/* KPI cards row */}
      <div className="grid gap-4 md:grid-cols-3">
        <Card className="border-red-200 dark:border-red-900/50 bg-red-50/60 dark:bg-red-950/25 shadow-sm">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-red-900 dark:text-red-300">Prioridade urgente</p>
                <p className="text-xs text-red-700 dark:text-red-400/70">Conversas com tratamento imediato</p>
              </div>
              <div className="text-right">
                <div className="text-2xl font-bold text-red-600 dark:text-red-400">{urgentConversations}</div>
                <AlertTriangle className="ml-auto h-5 w-5 text-red-500 dark:text-red-400" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-orange-200 dark:border-orange-900/50 bg-orange-50/60 dark:bg-orange-950/25 shadow-sm">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-orange-900 dark:text-orange-300">Nao lidas</p>
                <p className="text-xs text-orange-700 dark:text-orange-400/70">Mensagens pendentes para o admin</p>
              </div>
              <div className="text-right">
                <div className="text-2xl font-bold text-orange-600 dark:text-orange-400">{unreadMessages}</div>
                <Clock className="ml-auto h-5 w-5 text-orange-500 dark:text-orange-400" />
              </div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-blue-200 dark:border-blue-900/50 bg-blue-50/60 dark:bg-blue-950/25 shadow-sm">
          <CardContent className="p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm font-medium text-blue-900 dark:text-blue-300">Ativas</p>
                <p className="text-xs text-blue-700 dark:text-blue-400/70">Conversas abertas no monitoramento</p>
              </div>
              <div className="text-right">
                <div className="text-2xl font-bold text-blue-600 dark:text-blue-400">{activeConversations}</div>
                <Users className="ml-auto h-5 w-5 text-blue-500 dark:text-blue-400" />
              </div>
            </div>
          </CardContent>
        </Card>
      </div>

      <ChatStatsCards key={`stats-${refreshKey}`} />

      {/* 3-column layout */}
      <div className="grid gap-6 2xl:grid-cols-[360px_minmax(0,1fr)_340px]">
        <Card className="overflow-hidden border-border/70 shadow-sm">
          <CardHeader className="border-b">
            <CardTitle className="flex items-center justify-between gap-2 text-lg">
              <span className="flex items-center gap-2">
                <Users className="h-5 w-5" />
                Conversas
              </span>
              <Badge variant="outline">{`${activeConversations} ativas`}</Badge>
            </CardTitle>
          </CardHeader>
          <CardContent className="h-[460px] min-h-[360px] p-0 md:h-[520px] 2xl:h-[calc(100vh-22rem)] 2xl:min-h-[620px]">
            <ConversationsList
              key={`conversations-${refreshKey}`}
              onSelectConversation={handleSelectConversation}
              selectedConversationId={selectedConversation?.id}
              initialProtocolo={initialProtocolo}
              initialServicoId={initialServicoId}
              initialOrderId={initialOrderId}
              onConversationsLoaded={handleConversationsLoaded}
            />
          </CardContent>
        </Card>

        <div className="min-w-0 h-[620px] md:h-[720px] 2xl:h-[calc(100vh-22rem)] 2xl:min-h-[620px]">
          <ChatMessages conversation={selectedConversation} />
        </div>

        {selectedConversation ? (
          <div className="2xl:h-[calc(100vh-22rem)] 2xl:min-h-[620px]">
            <AdminActionsPanel conversation={selectedConversation} onUpdate={() => setRefreshKey((value) => value + 1)} />
          </div>
        ) : (
          <Card className="border-dashed border-border/80 shadow-sm">
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                <MessageCircle className="h-5 w-5" />
                Painel administrativo
              </CardTitle>
            </CardHeader>
            <CardContent className="flex min-h-[320px] flex-col items-center justify-center text-center text-sm text-muted-foreground">
              <MessageCircle className="mb-4 h-10 w-10 text-muted-foreground/35" />
              <p className="font-medium text-foreground">Selecione uma conversa para operar</p>
              <p className="mt-1 max-w-xs">
                Status, prioridade, responsável e notas internas aparecem aqui quando um atendimento for escolhido.
              </p>
            </CardContent>
          </Card>
        )}
      </div>

      {selectedConversation?.notes ? (
        <Card className="border-border/70 shadow-sm">
          <CardHeader>
            <CardTitle className="text-lg">Resumo da conversa selecionada</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm text-foreground">
            <div className="flex flex-wrap gap-2">
              <Badge variant="outline">Pedido: {selectedConversation.orderProtocol || selectedConversation.orderId}</Badge>
              {selectedConversation.assignedAdmin ? <Badge variant="outline">Responsavel: {selectedConversation.assignedAdmin}</Badge> : null}
            </div>
            <p className="whitespace-pre-wrap">{selectedConversation.notes}</p>
          </CardContent>
        </Card>
      ) : null}
    </div>
  )
}
