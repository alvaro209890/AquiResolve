"use client"

import { useEffect, useRef, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { useChatMessages, useChatActions } from "@/hooks/use-chat"
import { ChatMessage } from "@/types/chat"
import { LegacyChatConversation } from "@/lib/services/chat-service"
import { User, UserCheck, Shield, FileText, MapPin, AlertTriangle, Trash2, MoreVertical, Phone, Mail, Image, MessageCircle, Loader2, Paperclip } from "lucide-react"
import { formatDistanceToNow } from "date-fns"
import { ptBR } from "date-fns/locale"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs"
import { Textarea } from "@/components/ui/textarea"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { storage } from "@/lib/firebase"
import { getDownloadURL, ref, uploadBytes } from "firebase/storage"
import {
  ORDER_CHAT_THREAD_LABELS,
  detectOperationalSignalIds,
  OPERATIONAL_KEYWORD_PATTERNS,
  type OrderChatThreadType,
} from "@/lib/chat/order-chat-schema"
import { useAuth } from "@/components/auth-provider"
import { useToast } from "@/hooks/use-toast"

interface ChatMessagesProps {
  conversation: LegacyChatConversation | null
}

export function ChatMessages({ conversation }: ChatMessagesProps) {
  const messagesContainerRef = useRef<HTMLDivElement>(null)
  const fileInputRef = useRef<HTMLInputElement>(null)
  const { user } = useAuth()
  const { toast } = useToast()
  const isOrdersChat = Boolean(conversation?.id.startsWith("orders_"))
  const [threadTab, setThreadTab] = useState<OrderChatThreadType | "all">("all")
  const [composerChannel, setComposerChannel] = useState<OrderChatThreadType>("admin_internal")
  const [draft, setDraft] = useState("")
  const [uploadingAttachment, setUploadingAttachment] = useState(false)

  useEffect(() => {
    setThreadTab("all")
    setComposerChannel("admin_internal")
    setDraft("")
    setUploadingAttachment(false)
  }, [conversation?.id])

  const threadScope = isOrdersChat ? threadTab : "all"
  const { messages, loading, error } = useChatMessages(conversation?.id || "", { threadScope })
  const { deleteMessage, sendOrderThreadMessage, createOperationalAlert } = useChatActions()

  useEffect(() => {
    if (!messagesContainerRef.current) {
      return
    }

    messagesContainerRef.current.scrollTo({ top: 0, behavior: "auto" })
  }, [conversation?.id, threadTab])

  const scrollToLatest = () => {
    if (!messagesContainerRef.current) {
      return
    }

    messagesContainerRef.current.scrollTo({
      top: messagesContainerRef.current.scrollHeight,
      behavior: "smooth",
    })
  }

  const getSenderIcon = (senderType: ChatMessage["senderType"]) => {
    switch (senderType) {
      case "cliente":
        return <User className="h-4 w-4 text-blue-500" />
      case "prestador":
        return <UserCheck className="h-4 w-4 text-green-500" />
      case "admin":
        return <Shield className="h-4 w-4 text-orange-500" />
      default:
        return <User className="h-4 w-4 text-muted-foreground" />
    }
  }

  const getSenderColor = (senderType: ChatMessage["senderType"]) => {
    switch (senderType) {
      case "cliente":
        return "bg-blue-50 dark:bg-blue-950/30 border-blue-200 dark:border-blue-800"
      case "prestador":
        return "bg-emerald-50 dark:bg-emerald-950/30 border-emerald-200 dark:border-emerald-800"
      case "admin":
        return "bg-primary/5 border-primary/20"
      default:
        return "bg-muted/50 border-border"
    }
  }

  const getMessageIcon = (messageType: ChatMessage["messageType"]) => {
    switch (messageType) {
      case "image":
        return <Image className="h-4 w-4 text-green-500" />
      case "file":
        return <FileText className="h-4 w-4 text-blue-500" />
      case "location":
        return <MapPin className="h-4 w-4 text-red-500" />
      case "system":
        return <AlertTriangle className="h-4 w-4 text-muted-foreground" />
      default:
        return null
    }
  }

  const getImageUrl = (message: ChatMessage): string | undefined => {
    const url = message.metadata?.imageUrl ?? message.metadata?.mediaUrl ?? message.metadata?.attachmentUrl
    if (url) return url

    const documentUrl = message.metadata?.documentUrl
    if (!documentUrl) return undefined

    const extension = documentUrl.split(".").pop()?.split("?")[0]?.toLowerCase()
    return extension && ["jpg", "jpeg", "png", "gif", "webp", "bmp"].includes(extension)
      ? documentUrl
      : undefined
  }

  const getDocumentUrl = (message: ChatMessage): string | undefined => {
    return message.metadata?.documentUrl ?? message.metadata?.attachmentUrl
  }

  const sanitizeFileName = (fileName: string) => {
    return fileName.replace(/[^\w.\- ]+/g, "_").slice(0, 120) || "arquivo"
  }

  const handleDeleteMessage = async (message: ChatMessage) => {
    if (!confirm("Tem certeza que deseja deletar esta mensagem?")) {
      return
    }

    await deleteMessage(
      { id: message.id, chatId: message.chatId, content: message.content },
      "admin",
      "Administrador"
    )
  }

  const handleSendAdminMessage = async () => {
    if (!conversation || !isOrdersChat || !user || !draft.trim()) {
      return
    }
    const orderId = conversation.id.replace("orders_", "")
    const ok = await sendOrderThreadMessage({
      orderId,
      content: draft.trim(),
      threadType: composerChannel,
      senderId: user.uid,
      senderName: user.displayName || user.email || "Admin",
    })
    if (ok) {
      setDraft("")
      toast({ title: "Mensagem enviada", description: `Canal: ${ORDER_CHAT_THREAD_LABELS[composerChannel]}` })
    } else {
      toast({ title: "Falha ao enviar", variant: "destructive" })
    }
  }

  const handleAttachmentSelected = async (file: File | undefined) => {
    if (!file || !conversation || !isOrdersChat || !user) {
      return
    }
    if (!storage) {
      toast({ title: "Storage não inicializado", variant: "destructive" })
      return
    }

    const orderId = conversation.id.replace("orders_", "")
    const safeName = sanitizeFileName(file.name)
    const isImage = file.type.startsWith("image/")
    const content = draft.trim() || (isImage ? "Imagem enviada" : `Arquivo enviado: ${safeName}`)

    setUploadingAttachment(true)
    try {
      const storagePath = `chats/${orderId}/admin/${Date.now()}_${safeName}`
      const snapshot = await uploadBytes(ref(storage, storagePath), file, {
        contentType: file.type || "application/octet-stream",
      })
      const downloadUrl = await getDownloadURL(snapshot.ref)
      const ok = await sendOrderThreadMessage({
        orderId,
        content,
        threadType: composerChannel,
        senderId: user.uid,
        senderName: user.displayName || user.email || "Admin",
        messageType: isImage ? "image" : "file",
        imageUrl: isImage ? downloadUrl : undefined,
        documentUrl: isImage ? undefined : downloadUrl,
        fileName: safeName,
        fileSize: file.size,
        fileType: file.type || "application/octet-stream",
      })
      if (!ok) throw new Error("Falha ao gravar mensagem no Firestore")
      setDraft("")
      toast({ title: "Anexo enviado", description: `Canal: ${ORDER_CHAT_THREAD_LABELS[composerChannel]}` })
    } catch (e: unknown) {
      toast({
        title: "Falha ao enviar anexo",
        description: e instanceof Error ? e.message : String(e),
        variant: "destructive",
      })
    } finally {
      setUploadingAttachment(false)
      if (fileInputRef.current) fileInputRef.current.value = ""
    }
  }

  const handleCreateAlertFromMessage = async (message: ChatMessage) => {
    if (!conversation || !isOrdersChat || !user) {
      return
    }
    const signals = detectOperationalSignalIds(message.content)
    const orderId = conversation.id.replace("orders_", "")
    const id = await createOperationalAlert({
      orderId,
      protocol: conversation.orderProtocol,
      clientName: conversation.clientName,
      kind: signals[0] || "manual",
      severity: signals.includes("urgent") ? "critical" : signals.includes("call_base") ? "high" : "medium",
      title: signals[0] ? `Alerta: ${signals[0]}` : "Alerta operacional",
      detail: message.content.slice(0, 900),
      sourceMessageId: message.id,
      createdBy: user.uid,
    })
    if (id) {
      toast({ title: "Cartão operacional criado", description: "Documento em operationalAlerts." })
    } else {
      toast({ title: "Não foi possível criar o alerta", variant: "destructive" })
    }
  }

  if (!conversation) {
    return (
      <Card className="flex h-full">
        <CardContent className="flex h-full items-center justify-center">
          <div className="text-center text-muted-foreground">
            <User className="mx-auto mb-4 h-12 w-12 text-muted-foreground/30" />
            <p className="text-sm">Selecione uma conversa para visualizar as mensagens</p>
          </div>
        </CardContent>
      </Card>
    )
  }

  if (loading) {
    return (
      <Card className="flex h-full">
        <CardHeader>
          <CardTitle className="text-foreground">Carregando mensagens...</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="animate-skeleton rounded-lg border border-border p-4">
              <div className="mb-2 flex items-center space-x-2">
                <div className="h-4 w-4 rounded bg-muted" />
                <div className="h-4 w-24 rounded bg-muted" />
                <div className="h-3 w-16 rounded bg-muted" />
              </div>
              <div className="mb-2 h-4 w-full rounded bg-muted" />
              <div className="h-3 w-2/3 rounded bg-muted" />
            </div>
          ))}
        </CardContent>
      </Card>
    )
  }

  if (error) {
    return (
      <Card className="flex h-full">
        <CardContent className="flex h-full items-center justify-center">
          <div className="text-center">
            <AlertTriangle className="mx-auto mb-4 h-12 w-12 text-destructive" />
            <p className="text-sm text-destructive">Erro ao carregar mensagens: {error}</p>
          </div>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card className="flex h-full flex-col">
      <CardHeader className="border-b bg-muted/30">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
          <div className="min-w-0">
            <CardTitle className="text-lg text-foreground">{conversation.clientName}</CardTitle>
            <div className="mt-1 flex flex-wrap items-center gap-4 text-sm text-muted-foreground">
              <div className="flex items-center font-medium text-amber-900">
                <Phone className="mr-1 h-3 w-3" />
                {conversation.clientPhone || "Sem telefone — cadastre para contato proativo"}
              </div>
              <div className="flex items-center">
                <Mail className="mr-1 h-3 w-3" />
                {conversation.clientEmail || "Sem email"}
              </div>
              <Badge variant="outline" className="text-xs">
                {conversation.orderProtocol || conversation.orderId || "Sem pedido"}
              </Badge>
            </div>
            {conversation.providerName ? (
              <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-muted-foreground">
                <UserCheck className="h-3 w-3 text-emerald-600" />
                <span className="font-medium text-foreground">{conversation.providerName}</span>
                {conversation.providerPhone ? (
                  <span className="flex items-center gap-1 font-medium text-amber-900">
                    <Phone className="h-3 w-3" />
                    {conversation.providerPhone}
                  </span>
                ) : (
                  <span className="text-muted-foreground">Prestador sem telefone no pedido</span>
                )}
              </div>
            ) : null}
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="outline">{messages.length} mensagem{messages.length !== 1 ? "ens" : ""}</Badge>
            <Badge variant="secondary">{conversation.priority}</Badge>
            <Badge variant={conversation.status === "active" ? "default" : "secondary"}>{conversation.status}</Badge>
            <Button type="button" variant="outline" size="sm" onClick={scrollToLatest}>
              Últimas
            </Button>
          </div>
        </div>
      </CardHeader>

      {isOrdersChat ? (
        <div className="border-b bg-muted/40 px-3 py-2 sm:px-4">
          <Tabs value={threadTab} onValueChange={(value) => setThreadTab(value as OrderChatThreadType | "all")}>
            <TabsList className="flex h-auto min-h-9 flex-wrap justify-start gap-1 bg-background/80 p-1">
              <TabsTrigger value="all" className="text-xs">
                Visão consolidada
              </TabsTrigger>
              {(Object.keys(ORDER_CHAT_THREAD_LABELS) as OrderChatThreadType[]).map((key) => (
                <TabsTrigger key={key} value={key} className="max-w-[200px] truncate text-xs">
                  {ORDER_CHAT_THREAD_LABELS[key]}
                </TabsTrigger>
              ))}
            </TabsList>
          </Tabs>
          <p className="mt-2 text-[11px] leading-snug text-muted-foreground">
            Exemplos para teste: {OPERATIONAL_KEYWORD_PATTERNS.slice(0, 3).map((p) => `“${p.example}”`).join(" · ")}
          </p>
        </div>
      ) : null}

      <CardContent className="flex min-h-0 flex-1 flex-col overflow-hidden p-0">
        <div ref={messagesContainerRef} className="flex-1 space-y-4 overflow-y-auto p-4">
        {messages.length > 0 ? (
          <div className="space-y-3">
            {messages.map((message) => {
              const imageUrl = getImageUrl(message)
              const documentUrl = getDocumentUrl(message)

              return (
                <div key={message.id} className={`flex items-start space-x-3 rounded-lg border p-3 ${getSenderColor(message.senderType)}`}>
                  <div className="shrink-0">{getSenderIcon(message.senderType)}</div>

                  <div className="min-w-0 flex-1">
                    <div className="mb-1 flex flex-wrap items-center gap-2">
                      <span className="text-sm font-medium text-foreground">{message.senderName}</span>
                      <Badge variant="outline" className="text-[10px] font-normal">
                        {ORDER_CHAT_THREAD_LABELS[(message.threadType ?? "client_provider") as OrderChatThreadType]}
                      </Badge>
                      {message.visibility ? (
                        <Badge variant="secondary" className="text-[10px] font-normal">
                          {message.visibility}
                        </Badge>
                      ) : null}
                      <span className="text-xs text-muted-foreground">
                        {formatDistanceToNow(message.timestamp, {
                          addSuffix: true,
                          locale: ptBR,
                        })}
                      </span>
                      {getMessageIcon(message.messageType)}
                    </div>

                    <div className="space-y-2 text-sm text-foreground">
                      {imageUrl ? (
                        <div className="space-y-1">
                          <a href={imageUrl} target="_blank" rel="noopener noreferrer" className="block transition-opacity hover:opacity-90">
                            <img src={imageUrl} alt="Imagem do chat" className="max-h-64 max-w-xs cursor-pointer rounded-lg border object-contain" loading="lazy" />
                          </a>
                          <p className="text-xs text-muted-foreground">Clique para ampliar</p>
                        </div>
                      ) : null}

                      {message.messageType === "file" && documentUrl ? (
                        <a
                          href={documentUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="flex items-center space-x-2 rounded border border-border bg-card p-2 transition-colors hover:bg-muted"
                        >
                          <FileText className="h-4 w-4 text-blue-500" />
                          <span className="text-sm">{message.metadata?.fileName || "Arquivo"}</span>
                        </a>
                      ) : null}

                      {message.messageType === "location" && message.metadata?.location ? (
                        <div className="flex items-center space-x-2 rounded border border-border bg-card p-2">
                          <MapPin className="h-4 w-4 text-red-500" />
                          <span className="text-sm">{message.metadata.location.address}</span>
                        </div>
                      ) : null}

                      {message.content && !(imageUrl && /^Imagem enviada\.?$/i.test(message.content.trim())) ? (
                        <p className="whitespace-pre-wrap">{message.content}</p>
                      ) : null}
                    </div>
                  </div>

                  <DropdownMenu>
                    <DropdownMenuTrigger asChild>
                      <Button variant="ghost" size="sm" className="h-6 w-6 p-0">
                        <MoreVertical className="h-3 w-3" />
                      </Button>
                    </DropdownMenuTrigger>
                    <DropdownMenuContent align="end">
                      {isOrdersChat ? (
                        <DropdownMenuItem
                          onClick={() => {
                            void handleCreateAlertFromMessage(message)
                          }}
                        >
                          <AlertTriangle className="mr-2 h-4 w-4" />
                          Cartão de alerta
                        </DropdownMenuItem>
                      ) : null}
                      <DropdownMenuItem onClick={() => handleDeleteMessage(message)}>
                        <Trash2 className="mr-2 h-4 w-4" />
                        Deletar mensagem
                      </DropdownMenuItem>
                    </DropdownMenuContent>
                  </DropdownMenu>
                </div>
              )
            })}
          </div>
        ) : (
          <div className="flex h-64 flex-col items-center justify-center text-center text-muted-foreground">
            <MessageCircle className="mb-4 h-12 w-12 opacity-50" />
            <p className="text-sm">{isOrdersChat && threadTab !== "all" ? "Nenhuma mensagem neste canal." : "Nenhuma mensagem nesta conversa ainda."}</p>
            <p className="mt-1 text-xs">As mensagens reais do pedido aparecerao aqui.</p>
          </div>
        )}

        </div>

        {isOrdersChat && user ? (
          <div className="border-t bg-muted/30 p-3 sm:p-4">
            <p className="mb-2 text-xs text-muted-foreground">
              Envio administrativo em <strong>canais separados</strong>; cliente e prestador recebem apenas mensagens compatíveis com a visibilidade do canal.
            </p>
            <input
              ref={fileInputRef}
              type="file"
              className="hidden"
              onChange={(event) => void handleAttachmentSelected(event.target.files?.[0])}
            />
            <div className="flex flex-col gap-2 sm:flex-row sm:items-stretch">
              <div className="w-full sm:w-56">
                <Select value={composerChannel} onValueChange={(value) => setComposerChannel(value as OrderChatThreadType)}>
                  <SelectTrigger className="h-10">
                    <SelectValue placeholder="Canal" />
                  </SelectTrigger>
                  <SelectContent>
                    {(Object.keys(ORDER_CHAT_THREAD_LABELS) as OrderChatThreadType[]).map((key) => (
                      <SelectItem key={key} value={key}>
                        {ORDER_CHAT_THREAD_LABELS[key]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <Textarea
                value={draft}
                onChange={(e) => setDraft(e.target.value)}
                placeholder="Mensagem da base (não interrompe o thread principal se enviar em canal interno)…"
                className="min-h-20 flex-1 resize-none bg-background"
              />
              <Button
                type="button"
                variant="outline"
                className="h-10 shrink-0 sm:h-auto sm:self-end"
                disabled={uploadingAttachment}
                onClick={() => fileInputRef.current?.click()}
                aria-label="Anexar arquivo"
              >
                {uploadingAttachment ? <Loader2 className="h-4 w-4 animate-spin" /> : <Paperclip className="h-4 w-4" />}
              </Button>
              <Button type="button" className="h-10 shrink-0 sm:h-auto sm:self-end" onClick={() => void handleSendAdminMessage()}>
                Enviar
              </Button>
            </div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  )
}
