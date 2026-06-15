"use client"

import { useCallback, useEffect, useMemo, useState } from "react"
import { addDoc, collection, doc, getDoc, onSnapshot, orderBy, query, Timestamp, updateDoc, where, setDoc } from "firebase/firestore"
import { db } from "@/lib/firebase"
import { ChatConversation, ChatMessage, ChatStats, ChatFilter } from "@/types/chat"
import { ChatService, LegacyChatConversation } from "@/lib/services/chat-service"
import { messageMatchesThreadScope, normalizeMessageVisibility, normalizeThreadType, defaultVisibilityForThread, type OrderChatThreadType } from "@/lib/chat/order-chat-schema"

const CHAT_REFRESH_INTERVAL_MS = 15000

function applyConversationFilter(
  conversations: LegacyChatConversation[],
  filter?: ChatFilter
): LegacyChatConversation[] {
  let filtered = [...conversations]

  if (filter?.status) {
    filtered = filtered.filter((conversation) => conversation.status === filter.status)
  }

  if (filter?.priority) {
    filtered = filtered.filter((conversation) => conversation.priority === filter.priority)
  }

  if (filter?.searchTerm?.trim()) {
    const searchLower = filter.searchTerm.trim().toLowerCase()
    filtered = filtered.filter((conversation) =>
      conversation.clientName?.toLowerCase().includes(searchLower) ||
      conversation.clientEmail?.toLowerCase().includes(searchLower) ||
      conversation.orderId?.toLowerCase().includes(searchLower) ||
      conversation.orderProtocol?.toLowerCase().includes(searchLower) ||
      conversation.lastMessage?.content?.toLowerCase().includes(searchLower) ||
      conversation.assignedAdmin?.toLowerCase().includes(searchLower)
    )
  }

  if (filter?.hasUnread) {
    filtered = filtered.filter((conversation) => conversation.unreadCount.admin > 0)
  }

  if (filter?.providerSearch?.trim()) {
    const needle = filter.providerSearch.trim().toLowerCase()
    filtered = filtered.filter((conversation) => {
      const name = conversation.providerName?.toLowerCase() || ""
      const phone = conversation.providerPhone?.toLowerCase() || ""
      return name.includes(needle) || phone.includes(needle)
    })
  }

  if (filter?.protocolSearch?.trim()) {
    const needle = filter.protocolSearch.trim().toLowerCase()
    filtered = filtered.filter((conversation) => {
      const protocol = String(conversation.orderProtocol || "").toLowerCase()
      const id = String(conversation.orderId || "").toLowerCase()
      return protocol.includes(needle) || id.includes(needle)
    })
  }

  if (filter?.serviceOperationalStatus?.trim()) {
    const needle = filter.serviceOperationalStatus.trim().toLowerCase()
    filtered = filtered.filter((conversation) =>
      String(conversation.serviceOperationalStatus || "").toLowerCase().includes(needle)
    )
  }

  return filtered
}

function buildChatStats(conversations: LegacyChatConversation[]): ChatStats {
  return {
    totalConversations: conversations.length,
    activeConversations: conversations.filter((conversation) => conversation.status === "active").length,
    closedConversations: conversations.filter((conversation) => conversation.status === "closed").length,
    blockedConversations: conversations.filter((conversation) => conversation.status === "blocked").length,
    totalMessages: conversations.reduce((sum, conversation) => sum + (conversation.messageCount || (conversation.lastMessage ? 1 : 0)), 0),
    unreadMessages: conversations.reduce((sum, conversation) => sum + conversation.unreadCount.admin, 0),
    averageResponseTime: 0,
    conversationsByPriority: {
      low: conversations.filter((conversation) => conversation.priority === "low").length,
      medium: conversations.filter((conversation) => conversation.priority === "medium").length,
      high: conversations.filter((conversation) => conversation.priority === "high").length,
      urgent: conversations.filter((conversation) => conversation.priority === "urgent").length,
    },
    messagesByType: {
      text: conversations.filter((conversation) => conversation.lastMessage?.messageType === "text").length,
      image: conversations.filter((conversation) => conversation.lastMessage?.messageType === "image").length,
      file: conversations.filter((conversation) => conversation.lastMessage?.messageType === "file").length,
      location: conversations.filter((conversation) => conversation.lastMessage?.messageType === "location").length,
      system: conversations.filter((conversation) => conversation.lastMessage?.messageType === "system").length,
    },
  }
}

export function useChatConversations(filter?: ChatFilter, options?: { disabled?: boolean }) {
  const [conversations, setConversations] = useState<LegacyChatConversation[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const filterSnapshot = JSON.stringify(filter ?? {})
  const disabled = options?.disabled === true

  const fetchConversations = useCallback(async (silent = false) => {
    if (disabled) {
      return
    }
    try {
      if (!silent) {
        setLoading(true)
      }
      setError(null)

      const allConversations = await ChatService.getAllConversations()
      setConversations(applyConversationFilter(allConversations, filter))
    } catch {
      setError("Erro ao carregar conversas")
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [filterSnapshot, disabled])

  useEffect(() => {
    if (disabled) {
      setConversations([])
      setLoading(false)
      setError(null)
      return
    }

    let isActive = true

    const run = async () => {
      try {
        setLoading(true)
        setError(null)
        const allConversations = await ChatService.getAllConversations()
        if (!isActive) {
          return
        }
        setConversations(applyConversationFilter(allConversations, filter))
      } catch {
        if (isActive) {
          setError("Erro ao carregar conversas")
        }
      } finally {
        if (isActive) {
          setLoading(false)
        }
      }
    }

    run()
    const intervalId = window.setInterval(() => {
      fetchConversations(true)
    }, CHAT_REFRESH_INTERVAL_MS)

    return () => {
      isActive = false
      window.clearInterval(intervalId)
    }
  }, [fetchConversations, disabled])

  return { conversations, loading, error, refresh: () => fetchConversations() }
}

export function useChatMessages(chatId: string, options?: { threadScope?: OrderChatThreadType | "all" }) {
  const [rawMessages, setRawMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const threadScope = options?.threadScope ?? "all"

  const messages = useMemo(() => {
    if (threadScope === "all") {
      return rawMessages
    }
    return rawMessages.filter((message) => messageMatchesThreadScope(message, threadScope))
  }, [rawMessages, threadScope])

  useEffect(() => {
    const fetchMessages = async () => {
      if (!chatId) {
        setRawMessages([])
        setLoading(false)
        return
      }

      try {
        setLoading(true)
        setError(null)
        const conversationMessages = await ChatService.getConversationMessages(chatId)
        setRawMessages(conversationMessages)
      } catch {
        setError("Erro ao carregar mensagens")
      } finally {
        setLoading(false)
      }
    }

    fetchMessages()

    if (!chatId) {
      return
    }

    if (chatId.startsWith("legacy_") || chatId.startsWith("support_")) {
      const intervalId = window.setInterval(() => {
        fetchMessages()
      }, CHAT_REFRESH_INTERVAL_MS)

      return () => window.clearInterval(intervalId)
    }

    if (!db) {
      return
    }

    if (chatId.startsWith("orders_")) {
      const orderId = chatId.replace("orders_", "")
      const messagesRef = collection(db, "orders", orderId, "messages")
      const messagesQuery = query(messagesRef, orderBy("timestamp", "asc"))

      const unsubscribe = onSnapshot(
        messagesQuery,
        (snapshot) => {
          const data = snapshot.docs
            .map((snapshotDoc) => {
              const message = snapshotDoc.data()
              if (message.isDeleted) {
                return null
              }

              const imageUrl = message.imageUrl ?? message.image_url ?? message.mediaUrl ?? message.attachmentUrl ?? message.photoUrl ?? message.metadata?.imageUrl
              const documentUrl = message.documentUrl ?? message.fileUrl ?? message.metadata?.documentUrl
              const threadType = normalizeThreadType(message.threadType ?? message.channel)
              const visibility = normalizeMessageVisibility(message.visibility, threadType)

              return {
                id: snapshotDoc.id,
                chatId,
                senderId: message.senderId || message.clientId || "unknown",
                senderName: message.senderName || message.clientName || "Cliente",
                senderType:
                  ["provider", "prestador"].includes(String(message.senderType || "").toLowerCase())
                    ? "prestador"
                    : ["admin", "support", "system"].includes(String(message.senderType || "").toLowerCase())
                      ? "admin"
                      : "cliente",
                content: message.message ?? message.content ?? "",
                messageType: (message.messageType || "text") as ChatMessage["messageType"],
                timestamp: message.timestamp?.toDate?.() || new Date(),
                isRead: message.isRead ?? false,
                readBy: message.readBy || [],
                metadata: { ...message.metadata, imageUrl, documentUrl },
                threadType,
                visibility,
              } as ChatMessage
            })
            .filter(Boolean) as ChatMessage[]

          setRawMessages(data)
          setLoading(false)
        },
        () => setLoading(false)
      )

      return () => unsubscribe()
    }

    if (!chatId.startsWith("legacy_") && !chatId.startsWith("support_")) {
      // Mensagens ficam em orders/{orderId}/messages — chatMessages não existe como coleção top-level
      const messagesQuery = query(
        collection(db, "orders", chatId, "messages"),
        orderBy("timestamp", "asc")
      )

      const unsubscribe = onSnapshot(
        messagesQuery,
        (snapshot) => {
          const data = snapshot.docs
            .map((snapshotDoc) => {
              const message = snapshotDoc.data()
              if (message.isDeleted) {
                return null
              }

              return {
                id: snapshotDoc.id,
                ...message,
                timestamp: message.timestamp?.toDate?.() || new Date(),
                readBy: message.readBy || [],
                metadata: message.metadata || {},
              } as ChatMessage
            })
            .filter(Boolean) as ChatMessage[]

          setRawMessages(data)
          setLoading(false)
        },
        () => setLoading(false)
      )

      return () => unsubscribe()
    }
  }, [chatId])

  return { messages, loading, error }
}

export function useChatStats() {
  const [stats, setStats] = useState<ChatStats | null>(null)
  const [loading, setLoading] = useState(true)

  const fetchStats = useCallback(async (silent = false) => {
    try {
      if (!silent) {
        setLoading(true)
      }
      const allConversations = await ChatService.getAllConversations()
      setStats(buildChatStats(allConversations))
    } catch (error) {
      console.error("Erro ao calcular estatisticas:", error)
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }, [])

  useEffect(() => {
    fetchStats()
    const intervalId = window.setInterval(() => {
      fetchStats(true)
    }, CHAT_REFRESH_INTERVAL_MS)

    return () => window.clearInterval(intervalId)
  }, [fetchStats])

  return { stats, loading, refresh: () => fetchStats() }
}

function resolveMessageDocumentRef(message: Pick<ChatMessage, "id" | "chatId">) {
  if (!db) {
    return null
  }

  if (message.chatId.startsWith("orders_")) {
    const orderId = message.chatId.replace("orders_", "")
    return doc(db, "orders", orderId, "messages", message.id)
  }

  if (message.chatId.startsWith("legacy_")) {
    const orderId = message.chatId.replace("legacy_", "")
    return doc(db, `order_${orderId}_messages`, message.id)
  }

  if (message.chatId.startsWith("support_")) {
    return doc(db, "support_messages", message.id)
  }

  return doc(db, "chatMessages", message.id)
}

export function useChatActions() {
  const [loading, setLoading] = useState(false)

  const upsertConversationMonitoring = useCallback(async (chatId: string, data: Record<string, unknown>) => {
    if (!db) {
      return false
    }

    await setDoc(
      doc(db, "chatMonitoring", chatId),
      {
        ...data,
        updatedAt: Timestamp.now(),
      },
      { merge: true }
    )

    return true
  }, [])

  const logAdminAction = useCallback(async (payload: Record<string, unknown>) => {
    if (!db) {
      return
    }

    await addDoc(collection(db, "adminActions"), {
      ...payload,
      timestamp: Timestamp.now(),
    })
  }, [])

  const updateConversationStatus = useCallback(async (chatId: string, status: ChatConversation["status"]) => {
    if (!db) return false

    setLoading(true)
    try {
      await upsertConversationMonitoring(chatId, { status })
      await logAdminAction({
        chatId,
        adminId: "admin",
        adminName: "Administrador",
        action: status === "blocked" ? "block" : status === "archived" ? "archive" : "unassign",
        details: `Status alterado para ${status}`,
      })
      return true
    } catch {
      return false
    } finally {
      setLoading(false)
    }
  }, [logAdminAction, upsertConversationMonitoring])

  const updateConversationPriority = useCallback(async (chatId: string, priority: ChatConversation["priority"]) => {
    if (!db) return false

    setLoading(true)
    try {
      await upsertConversationMonitoring(chatId, { priority })
      await logAdminAction({
        chatId,
        adminId: "admin",
        adminName: "Administrador",
        action: "priority_change",
        details: `Prioridade alterada para ${priority}`,
      })
      return true
    } catch (error) {
      console.error("Erro ao atualizar prioridade:", error)
      return false
    } finally {
      setLoading(false)
    }
  }, [logAdminAction, upsertConversationMonitoring])

  const assignConversation = useCallback(async (chatId: string, adminId: string, adminName: string) => {
    if (!db) return false

    setLoading(true)
    try {
      await upsertConversationMonitoring(chatId, {
        assignedAdmin: adminName || adminId,
      })

      await logAdminAction({
        chatId,
        adminId,
        adminName,
        action: "assign",
        details: `Conversa atribuida para ${adminName}`,
      })

      return true
    } catch {
      return false
    } finally {
      setLoading(false)
    }
  }, [logAdminAction, upsertConversationMonitoring])

  const addConversationNote = useCallback(async (chatId: string, note: string, adminId: string, adminName: string) => {
    if (!db) return false

    setLoading(true)
    try {
      const monitoringRef = doc(db, "chatMonitoring", chatId)
      const monitoringDoc = await getDoc(monitoringRef)
      const currentNotes = typeof monitoringDoc.data()?.notes === "string" ? monitoringDoc.data()?.notes : ""
      const newEntry = `[${new Date().toLocaleString("pt-BR")}] ${adminName}: ${note}`
      const notes = currentNotes ? `${currentNotes}\n\n${newEntry}` : newEntry

      await upsertConversationMonitoring(chatId, { notes })
      await logAdminAction({
        chatId,
        adminId,
        adminName,
        action: "note_add",
        details: note,
      })

      return true
    } catch (error) {
      console.error("Erro ao adicionar nota:", error)
      return false
    } finally {
      setLoading(false)
    }
  }, [logAdminAction, upsertConversationMonitoring])

  const sendOrderThreadMessage = useCallback(
    async (params: {
      orderId: string
      content: string
      threadType: OrderChatThreadType
      senderId: string
      senderName: string
      messageType?: ChatMessage["messageType"]
      imageUrl?: string
      documentUrl?: string
      fileName?: string
      fileSize?: number
      fileType?: string
    }) => {
      const content = params.content.trim()
      const hasAttachment = Boolean(params.imageUrl || params.documentUrl)
      if (!db || (!content && !hasAttachment)) {
        return false
      }

      setLoading(true)
      try {
        const visibility = defaultVisibilityForThread(params.threadType)
        const messageType = params.messageType || (params.imageUrl ? "image" : params.documentUrl ? "file" : "text")
        const metadata = {
          imageUrl: params.imageUrl || null,
          documentUrl: params.documentUrl || null,
          fileName: params.fileName || null,
          fileSize: params.fileSize || null,
          fileType: params.fileType || null,
        }
        await addDoc(collection(db, "orders", params.orderId, "messages"), {
          message: content,
          content,
          timestamp: Timestamp.now(),
          senderType: "admin",
          senderId: params.senderId,
          senderName: params.senderName,
          threadType: params.threadType,
          visibility,
          messageType,
          imageUrl: params.imageUrl || null,
          documentUrl: params.documentUrl || null,
          fileName: params.fileName || null,
          fileSize: params.fileSize || null,
          fileType: params.fileType || null,
          metadata,
          isRead: true,
          readBy: [params.senderId],
        })

        await logAdminAction({
          chatId: `orders_${params.orderId}`,
          adminId: params.senderId,
          adminName: params.senderName,
          action: "note_add",
          details: `[canal:${params.threadType}/${messageType}] ${content.slice(0, 240) || params.fileName || "anexo"}`,
        })

        return true
      } catch {
        return false
      } finally {
        setLoading(false)
      }
    },
    [logAdminAction]
  )

  const createOperationalAlert = useCallback(
    async (input: {
      orderId: string
      protocol?: string
      clientName?: string
      kind: string
      severity: "low" | "medium" | "high" | "critical"
      title: string
      detail: string
      sourceMessageId?: string
      createdBy?: string
    }) => {
      if (!db) {
        return null
      }

      try {
        const ref = await addDoc(collection(db, "operationalAlerts"), {
          ...input,
          status: "open",
          createdAt: Timestamp.now(),
        })
        await logAdminAction({
          chatId: `orders_${input.orderId}`,
          adminId: input.createdBy || "admin",
          adminName: "Administrador",
          action: "note_add",
          details: `[alerta:${input.kind}/${input.severity}] ${input.title}`,
        })
        return ref.id
      } catch {
        return null
      }
    },
    [logAdminAction]
  )

  const acknowledgeOperationalAlert = useCallback(async (alertId: string, adminId: string) => {
    if (!db) {
      return false
    }

    try {
      await updateDoc(doc(db, "operationalAlerts", alertId), {
        status: "acknowledged",
        acknowledgedAt: Timestamp.now(),
        acknowledgedBy: adminId,
      })
      await logAdminAction({
        chatId: alertId,
        adminId,
        adminName: "Administrador",
        action: "note_add",
        details: "Alerta operacional reconhecido",
      })
      return true
    } catch {
      return false
    }
  }, [logAdminAction])

  const deleteMessage = useCallback(async (message: Pick<ChatMessage, "id" | "chatId" | "content">, adminId: string, adminName: string) => {
    if (!db) return false

    const messageRef = resolveMessageDocumentRef(message)
    if (!messageRef) {
      return false
    }

    setLoading(true)
    try {
      const messageDoc = await getDoc(messageRef)
      if (!messageDoc.exists()) {
        return false
      }

      await updateDoc(messageRef, {
        isDeleted: true,
        deletedAt: Timestamp.now(),
        deletedBy: adminId,
      })

      await logAdminAction({
        chatId: message.chatId,
        adminId,
        adminName,
        action: "message_delete",
        details: `Mensagem deletada: \"${message.content.substring(0, 50)}\"`,
      })

      return true
    } catch {
      return false
    } finally {
      setLoading(false)
    }
  }, [logAdminAction])

  return {
    loading,
    updateConversationStatus,
    updateConversationPriority,
    assignConversation,
    addConversationNote,
    sendOrderThreadMessage,
    createOperationalAlert,
    acknowledgeOperationalAlert,
    deleteMessage,
  }
}
