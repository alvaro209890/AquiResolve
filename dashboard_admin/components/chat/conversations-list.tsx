"use client"

import { useEffect, useMemo, useState } from "react"
import { Badge } from "@/components/ui/badge"
import { Input } from "@/components/ui/input"
import { useChatConversations } from "@/hooks/use-chat"
import { LegacyChatConversation } from "@/lib/services/chat-service"
import { Search, MessageSquare, Clock, AlertTriangle, Mail, UserCheck } from "lucide-react"
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"

interface ConversationsListProps {
  onSelectConversation: (conversation: LegacyChatConversation) => void
  selectedConversationId?: string
  initialProtocolo?: string | null
  initialServicoId?: string | null
  initialOrderId?: string | null
  onConversationsLoaded?: (conversations: LegacyChatConversation[]) => void
  compact?: boolean
  /** Quando definido, ignora o fetch interno e usa estas conversas (ex.: dashboard operacional com filtros externos). */
  conversationsOverride?: LegacyChatConversation[]
  loadingOverride?: boolean
}

export function ConversationsList({
  onSelectConversation,
  selectedConversationId,
  initialProtocolo,
  initialServicoId,
  initialOrderId,
  onConversationsLoaded,
  conversationsOverride,
  loadingOverride,
}: ConversationsListProps) {
  const [searchTerm, setSearchTerm] = useState("")
  const useExternal = conversationsOverride !== undefined
  const internal = useChatConversations({ searchTerm }, { disabled: useExternal })
  const source = useExternal ? conversationsOverride! : internal.conversations
  const conversations = useMemo(() => {
    if (!searchTerm.trim()) {
      return source
    }
    const s = searchTerm.trim().toLowerCase()
    return source.filter(
      (conversation) =>
        conversation.clientName?.toLowerCase().includes(s) ||
        conversation.clientEmail?.toLowerCase().includes(s) ||
        conversation.orderId?.toLowerCase().includes(s) ||
        String(conversation.orderProtocol || "").toLowerCase().includes(s) ||
        conversation.providerName?.toLowerCase().includes(s) ||
        conversation.assignedAdmin?.toLowerCase().includes(s)
    )
  }, [source, searchTerm])

  const loading = useExternal ? Boolean(loadingOverride) : internal.loading
  const error = useExternal ? null : internal.error

  useEffect(() => {
    onConversationsLoaded?.(conversations)
  }, [conversations, onConversationsLoaded])

  useEffect(() => {
    if (!(initialProtocolo || initialOrderId || initialServicoId) || conversations.length === 0) {
      return
    }

    const matchingConversation = conversations.find(
      (conversation) =>
        conversation.orderProtocol === initialProtocolo ||
        conversation.orderId === initialServicoId ||
        conversation.orderId === initialOrderId
    )

    if (matchingConversation && matchingConversation.id !== selectedConversationId) {
      onSelectConversation(matchingConversation)
    }
  }, [conversations, initialOrderId, initialProtocolo, initialServicoId, onSelectConversation, selectedConversationId])

  const getStatusIcon = (status: LegacyChatConversation["status"]) => {
    switch (status) {
      case "active":
        return <MessageSquare className="h-4 w-4 text-green-500" />
      case "closed":
        return <Clock className="h-4 w-4 text-muted-foreground" />
      case "blocked":
        return <AlertTriangle className="h-4 w-4 text-red-500" />
      default:
        return <MessageSquare className="h-4 w-4 text-muted-foreground/60" />
    }
  }

  const getPriorityColor = (priority: LegacyChatConversation["priority"]) => {
    switch (priority) {
      case "urgent":
        return "bg-red-100 dark:bg-red-950/60 text-red-800 dark:text-red-300"
      case "high":
        return "bg-orange-100 dark:bg-orange-950/60 text-orange-800 dark:text-orange-300"
      case "medium":
        return "bg-yellow-100 dark:bg-yellow-950/60 text-yellow-800 dark:text-yellow-300"
      case "low":
        return "bg-green-100 dark:bg-green-950/60 text-green-800 dark:text-green-300"
      default:
        return "bg-muted dark:bg-muted text-muted-foreground dark:text-muted-foreground"
    }
  }

  const getStatusColor = (status: LegacyChatConversation["status"]) => {
    switch (status) {
      case "active":
        return "bg-emerald-100 dark:bg-emerald-950/60 text-emerald-800 dark:text-emerald-300"
      case "closed":
        return "bg-muted dark:bg-muted text-muted-foreground dark:text-muted-foreground"
      case "archived":
        return "bg-blue-100 dark:bg-blue-950/60 text-blue-800 dark:text-blue-300"
      case "blocked":
        return "bg-red-100 dark:bg-red-950/60 text-red-800 dark:text-red-300"
      default:
        return "bg-muted dark:bg-muted text-muted-foreground dark:text-muted-foreground"
    }
  }

  if (loading) {
    return (
      <div className="flex h-full flex-col">
        <div className="border-b border-border bg-card p-4">
          <h3 className="font-semibold text-foreground">Carregando...</h3>
        </div>
        <div className="flex-1 space-y-4 p-4">
          {[...Array(5)].map((_, index) => (
            <div key={index} className="rounded-lg border border-border p-4 animate-skeleton">
              <div className="mb-2 flex items-center justify-between">
                <div className="h-4 w-1/3 rounded bg-muted"></div>
                <div className="h-4 w-16 rounded bg-muted"></div>
              </div>
              <div className="mb-2 h-3 w-2/3 rounded bg-muted"></div>
              <div className="h-3 w-1/2 rounded bg-muted"></div>
            </div>
          ))}
        </div>
      </div>
    )
  }

  if (error) {
    return (
      <div className="flex h-full flex-col">
        <div className="border-b border-border bg-card p-4">
          <h3 className="font-semibold text-foreground">Erro</h3>
        </div>
        <div className="flex-1 p-6 text-center">
          <AlertTriangle className="mx-auto mb-4 h-12 w-12 text-destructive" />
          <p className="text-sm text-destructive">Erro ao carregar conversas: {error}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-border bg-card p-4">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="font-semibold text-foreground">Conversas ({conversations.length})</h3>
        </div>
        <div className="relative">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground pointer-events-none" />
          <Input
            placeholder="Buscar por cliente, pedido ou responsavel"
            value={searchTerm}
            onChange={(event) => setSearchTerm(event.target.value)}
            className="h-9 pl-10 text-sm"
          />
        </div>
      </div>

      <div className="flex-1 overflow-y-auto">
        {conversations.length === 0 ? (
          <div className="p-6 text-center text-muted-foreground">
            <MessageSquare className="mx-auto mb-4 h-12 w-12 text-muted-foreground/30" />
            <p className="text-sm">Nenhuma conversa encontrada</p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {conversations.map((conversation) => (
              <div
                key={conversation.id}
                className={`cursor-pointer p-4 transition-all duration-200 hover:bg-muted/50 ${
                  selectedConversationId === conversation.id ? "border-l-4 border-l-primary bg-primary/5 pl-3" : ""
                }`}
                onClick={() => onSelectConversation(conversation)}
              >
                <div className="space-y-2">
                  <div className="flex items-center space-x-2">
                    {getStatusIcon(conversation.status)}
                    <h4 className="flex-1 truncate text-sm font-medium text-foreground">{conversation.clientName}</h4>
                    {conversation.unreadCount.admin > 0 ? (
                      <div className="shrink-0 rounded-full bg-primary px-2 py-1 text-xs text-primary-foreground">
                        {conversation.unreadCount.admin}
                      </div>
                    ) : null}
                  </div>

                  <p className="truncate text-xs text-muted-foreground">
                    {conversation.orderId !== "suporte-geral"
                      ? `Pedido: ${conversation.orderProtocol || conversation.orderId}`
                      : "Suporte geral"}
                  </p>

                  <div className="flex flex-wrap items-center gap-2">
                    <Badge className={`px-2 py-1 text-xs ${getPriorityColor(conversation.priority)}`}>
                      {conversation.priority}
                    </Badge>
                    <Badge className={`px-2 py-1 text-xs ${getStatusColor(conversation.status)}`}>
                      {conversation.status}
                    </Badge>
                    {conversation.assignedAdmin ? (
                      <Badge variant="outline" className="px-2 py-1 text-xs">
                        <UserCheck className="mr-1 h-3 w-3" />
                        {conversation.assignedAdmin}
                      </Badge>
                    ) : null}
                  </div>

                  <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
                    <div className="flex min-w-0 items-center space-x-1">
                      <Mail className="h-3 w-3 shrink-0" />
                      <span className="truncate">{conversation.clientEmail || "Sem email"}</span>
                    </div>

                    {conversation.lastMessage ? (
                      <div className="flex shrink-0 items-center space-x-1">
                        <Clock className="h-3 w-3" />
                        <span className="whitespace-nowrap">
                          {formatDistanceToNow(conversation.lastMessage.timestamp, {
                            addSuffix: true,
                            locale: ptBR,
                          })}
                        </span>
                      </div>
                    ) : null}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
