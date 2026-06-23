"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { PageWithBack } from "@/components/layout/page-with-back"
import { Card, CardContent } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
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
import { ScrollArea } from "@/components/ui/scroll-area"
import {
  Wrench,
  Megaphone,
  Send,
  RefreshCw,
  Pin,
  Archive,
  Search,
  Plus,
} from "lucide-react"
import { useAuth } from "@/components/auth-provider"
import { adminFetch } from "@/lib/admin-api"

interface ChatMeta {
  id: string
  providerId: string
  providerName: string
  providerEmail?: string
  lastMessage?: string
  lastMessageAt?: { seconds: number } | null
  lastSender?: "admin" | "provider"
  unreadByAdmin?: number
  unreadByProvider?: number
  pinned?: boolean
  archived?: boolean
}

interface ChatMessage {
  id: string
  text: string
  senderType: "admin" | "provider"
  senderName?: string
  type?: "text" | "promotion" | "notice" | "order_update"
  relatedOrderId?: string
  createdAt?: { seconds: number } | null
  broadcastId?: string
}

function fmtRelative(ts?: { seconds: number } | null) {
  if (!ts) return ""
  const date = new Date(ts.seconds * 1000)
  const now = new Date()
  const diff = (now.getTime() - date.getTime()) / 1000
  if (diff < 60) return "agora"
  if (diff < 3600) return `${Math.floor(diff / 60)} min`
  if (diff < 86400) return `${Math.floor(diff / 3600)} h`
  return date.toLocaleDateString("pt-BR")
}

const TYPE_LABEL: Record<NonNullable<ChatMessage["type"]>, { label: string; tone: string }> = {
  text: { label: "", tone: "" },
  promotion: { label: "Promoção", tone: "bg-amber-100 text-amber-900 dark:bg-amber-950/40 dark:text-amber-200" },
  notice: { label: "Aviso", tone: "bg-blue-100 text-blue-900 dark:bg-blue-950/40 dark:text-blue-200" },
  order_update: { label: "Pedido", tone: "bg-violet-100 text-violet-900 dark:bg-violet-950/40 dark:text-violet-200" },
}

export default function ChatPrestadoresPage() {
  const { user } = useAuth()
  const [chats, setChats] = useState<ChatMeta[]>([])
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [selected, setSelected] = useState<ChatMeta | null>(null)
  const [filter, setFilter] = useState<"active" | "archived">("active")
  const [unreadOnly, setUnreadOnly] = useState(false)
  const [search, setSearch] = useState("")
  const [draft, setDraft] = useState("")
  const [draftType, setDraftType] = useState<"text" | "promotion" | "notice" | "order_update">("text")
  const [loadingChats, setLoadingChats] = useState(true)
  const [loadingMsgs, setLoadingMsgs] = useState(false)
  const [sending, setSending] = useState(false)
  const [broadcastOpen, setBroadcastOpen] = useState(false)
  const [newChatOpen, setNewChatOpen] = useState(false)
  const scrollEndRef = useRef<HTMLDivElement | null>(null)

  // Lista de chats
  const loadChats = useCallback(async () => {
    try {
      const qs = new URLSearchParams({
        status: filter,
        ...(unreadOnly ? { unreadOnly: "true" } : {}),
      })
      const res = await adminFetch(`/api/provider-chats?${qs.toString()}`, { cache: "no-store" })
      const json = await res.json()
      if (json.success) setChats(json.chats as ChatMeta[])
    } finally {
      setLoadingChats(false)
    }
  }, [filter, unreadOnly])

  useEffect(() => {
    loadChats()
    const t = setInterval(loadChats, 8000)
    return () => clearInterval(t)
  }, [loadChats])

  // Mensagens do chat selecionado
  const loadMessages = useCallback(async (providerId: string, silent = false) => {
    if (!silent) setLoadingMsgs(true)
    try {
      const res = await adminFetch(`/api/provider-chats/${providerId}/messages?limit=200`, { cache: "no-store" })
      const json = await res.json()
      if (json.success) setMessages(json.messages as ChatMessage[])
    } finally {
      if (!silent) setLoadingMsgs(false)
    }
  }, [])

  // Marca como lido pelo admin ao selecionar (só se o chat já existe, p/ não criar doc-fantasma)
  useEffect(() => {
    if (!selected) return
    loadMessages(selected.providerId)
    const exists = chats.some((c) => c.providerId === selected.providerId)
    if (exists) {
      adminFetch(`/api/provider-chats/${selected.providerId}/read?role=admin`, { method: "PATCH" }).catch(() => null)
    }
    const t = setInterval(() => loadMessages(selected.providerId, true), 5000)
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected, loadMessages])

  // Auto-scroll ao final quando chegam novas mensagens
  useEffect(() => {
    scrollEndRef.current?.scrollIntoView({ behavior: "smooth" })
  }, [messages])

  const filteredChats = useMemo(() => {
    const q = search.trim().toLowerCase()
    const list = [...chats]
    list.sort((a, b) => {
      if ((a.pinned ?? false) !== (b.pinned ?? false)) return a.pinned ? -1 : 1
      const ta = a.lastMessageAt?.seconds ?? 0
      const tb = b.lastMessageAt?.seconds ?? 0
      return tb - ta
    })
    if (!q) return list
    return list.filter(
      (c) =>
        (c.providerName ?? "").toLowerCase().includes(q) ||
        (c.providerEmail ?? "").toLowerCase().includes(q) ||
        c.providerId.toLowerCase().includes(q)
    )
  }, [chats, search])

  async function sendMessage() {
    if (!selected || !draft.trim() || sending) return
    setSending(true)
    try {
      const res = await adminFetch(`/api/provider-chats/${selected.providerId}/messages`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          text: draft.trim(),
          type: draftType,
          adminId: user?.uid,
          adminName: user?.displayName ?? user?.email ?? "Central",
        }),
      })
      const json = await res.json()
      if (!json.success) {
        alert(json.error ?? "Erro ao enviar")
        return
      }
      setDraft("")
      setDraftType("text")
      await loadMessages(selected.providerId)
      await loadChats()
    } finally {
      setSending(false)
    }
  }

  async function togglePin(c: ChatMeta) {
    await adminFetch(`/api/provider-chats/${c.providerId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ pinned: !c.pinned }),
    })
    loadChats()
  }

  async function toggleArchive(c: ChatMeta) {
    await adminFetch(`/api/provider-chats/${c.providerId}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ archived: !c.archived }),
    })
    if (selected?.providerId === c.providerId) setSelected(null)
    loadChats()
  }

  function startConversation(p: { id: string; name: string; email?: string }) {
    // Abre a conversa (o doc nasce ao enviar a 1ª mensagem). Se já existe, usa o meta da lista.
    const existing = chats.find((c) => c.providerId === p.id)
    setSelected(
      existing ?? {
        id: p.id,
        providerId: p.id,
        providerName: p.name,
        providerEmail: p.email,
      }
    )
    setNewChatOpen(false)
  }

  return (
    <PageWithBack backButtonLabel="Voltar">
      <div className="space-y-4">
        {/* Header */}
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
              <Wrench className="h-5 w-5 text-primary" />
            </div>
            <div>
              <h1 className="text-2xl font-bold tracking-tight text-foreground">Chat com Prestadores</h1>
              <p className="text-sm text-muted-foreground">
                Envie avisos, orientações e atualizações aos prestadores — individual ou em massa
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="outline" onClick={() => setNewChatOpen(true)} className="gap-2">
              <Plus className="h-4 w-4" />
              Nova conversa
            </Button>
            <Button onClick={() => setBroadcastOpen(true)} className="gap-2">
              <Megaphone className="h-4 w-4" />
              Broadcast
            </Button>
          </div>
        </div>

        {/* Grid: lista | conversa */}
        <div className="grid grid-cols-1 lg:grid-cols-[340px_1fr] gap-4 h-[calc(100vh-220px)] min-h-[500px]">
          {/* Sidebar — lista de chats */}
          <Card className="flex flex-col overflow-hidden">
            <div className="border-b border-border p-3 space-y-2">
              <div className="relative">
                <Search className="h-4 w-4 absolute left-2.5 top-2.5 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Buscar prestador..."
                  className="pl-8"
                />
              </div>
              <div className="flex items-center gap-2">
                <Select value={filter} onValueChange={(v) => setFilter(v as "active" | "archived")}>
                  <SelectTrigger className="h-8 flex-1">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="active">Ativos</SelectItem>
                    <SelectItem value="archived">Arquivados</SelectItem>
                  </SelectContent>
                </Select>
                <Button
                  variant={unreadOnly ? "default" : "outline"}
                  size="sm"
                  className="h-8 text-xs"
                  onClick={() => setUnreadOnly((v) => !v)}
                >
                  Não lidas
                </Button>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8"
                  onClick={loadChats}
                  disabled={loadingChats}
                  aria-label="Atualizar"
                >
                  <RefreshCw className={`h-4 w-4 ${loadingChats ? "animate-spin" : ""}`} />
                </Button>
              </div>
            </div>
            <ScrollArea className="flex-1">
              {filteredChats.length === 0 ? (
                <div className="p-6 text-center text-sm text-muted-foreground">
                  {loadingChats ? "Carregando..." : "Nenhuma conversa. Use \"Nova conversa\" para começar."}
                </div>
              ) : (
                <ul className="divide-y divide-border">
                  {filteredChats.map((c) => {
                    const active = selected?.providerId === c.providerId
                    return (
                      <li key={c.id}>
                        <button
                          onClick={() => setSelected(c)}
                          className={`w-full text-left px-3 py-3 hover:bg-muted/60 transition-colors ${
                            active ? "bg-muted" : ""
                          }`}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div className="min-w-0 flex-1">
                              <div className="flex items-center gap-1.5">
                                {c.pinned && <Pin className="h-3 w-3 text-amber-600" />}
                                <p className="text-sm font-medium text-foreground truncate">
                                  {c.providerName || "Prestador"}
                                </p>
                              </div>
                              <p className="text-xs text-muted-foreground truncate mt-0.5">
                                {c.lastSender === "admin" && "Você: "}
                                {c.lastMessage || "—"}
                              </p>
                            </div>
                            <div className="flex flex-col items-end gap-1 shrink-0">
                              <span className="text-[10px] text-muted-foreground">
                                {fmtRelative(c.lastMessageAt)}
                              </span>
                              {(c.unreadByAdmin ?? 0) > 0 && (
                                <Badge variant="destructive" className="h-4 px-1.5 text-[10px]">
                                  {c.unreadByAdmin}
                                </Badge>
                              )}
                            </div>
                          </div>
                        </button>
                      </li>
                    )
                  })}
                </ul>
              )}
            </ScrollArea>
          </Card>

          {/* Painel — conversa */}
          <Card className="flex flex-col overflow-hidden">
            {!selected ? (
              <CardContent className="flex-1 flex items-center justify-center text-sm text-muted-foreground">
                Selecione uma conversa ou inicie uma nova
              </CardContent>
            ) : (
              <>
                <div className="border-b border-border p-3 flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    <p className="text-sm font-semibold truncate">{selected.providerName}</p>
                    <p className="text-xs text-muted-foreground truncate">{selected.providerEmail ?? selected.providerId}</p>
                  </div>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="icon" onClick={() => togglePin(selected)} title="Fixar">
                      <Pin className={`h-4 w-4 ${selected.pinned ? "text-amber-600 fill-current" : ""}`} />
                    </Button>
                    <Button variant="ghost" size="icon" onClick={() => toggleArchive(selected)} title="Arquivar">
                      <Archive className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
                <ScrollArea className="flex-1 px-4 py-4 bg-muted/20">
                  {loadingMsgs ? (
                    <p className="text-center text-sm text-muted-foreground py-8">Carregando…</p>
                  ) : messages.length === 0 ? (
                    <p className="text-center text-sm text-muted-foreground py-8">
                      Sem mensagens ainda. Envie a primeira.
                    </p>
                  ) : (
                    <div className="space-y-2">
                      {messages.map((m) => {
                        const isAdmin = m.senderType === "admin"
                        const typeInfo = m.type && m.type !== "text" ? TYPE_LABEL[m.type] : null
                        return (
                          <div
                            key={m.id}
                            className={`flex ${isAdmin ? "justify-end" : "justify-start"}`}
                          >
                            <div
                              className={`max-w-[78%] rounded-lg px-3 py-2 shadow-sm ${
                                isAdmin
                                  ? "bg-primary text-primary-foreground"
                                  : "bg-card border border-border text-foreground"
                              }`}
                            >
                              {typeInfo && (
                                <div className={`text-[10px] font-semibold uppercase tracking-wide mb-1 px-1.5 py-0.5 rounded inline-block ${typeInfo.tone}`}>
                                  {typeInfo.label}
                                </div>
                              )}
                              <p className="text-sm whitespace-pre-wrap break-words">{m.text}</p>
                              <p className={`text-[10px] mt-1 ${isAdmin ? "text-primary-foreground/70" : "text-muted-foreground"}`}>
                                {fmtRelative(m.createdAt)} {m.broadcastId && "· broadcast"}
                              </p>
                            </div>
                          </div>
                        )
                      })}
                      <div ref={scrollEndRef} />
                    </div>
                  )}
                </ScrollArea>
                <div className="border-t border-border p-3 space-y-2">
                  <div className="flex items-center gap-2">
                    <Select value={draftType} onValueChange={(v) => setDraftType(v as typeof draftType)}>
                      <SelectTrigger className="w-36 h-8 text-xs">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="text">Mensagem</SelectItem>
                        <SelectItem value="promotion">Promoção</SelectItem>
                        <SelectItem value="notice">Aviso</SelectItem>
                        <SelectItem value="order_update">Pedido</SelectItem>
                      </SelectContent>
                    </Select>
                    <span className="text-[10px] text-muted-foreground">{draft.length}/2000</span>
                  </div>
                  <div className="flex items-end gap-2">
                    <Textarea
                      value={draft}
                      onChange={(e) => setDraft(e.target.value)}
                      placeholder="Digite uma mensagem..."
                      rows={2}
                      className="resize-none"
                      maxLength={2000}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" && !e.shiftKey) {
                          e.preventDefault()
                          sendMessage()
                        }
                      }}
                    />
                    <Button
                      onClick={sendMessage}
                      disabled={sending || !draft.trim()}
                      className="shrink-0"
                    >
                      <Send className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </>
            )}
          </Card>
        </div>
      </div>

      {/* Modal nova conversa */}
      <NewConversationDialog
        open={newChatOpen}
        onOpenChange={setNewChatOpen}
        onPick={startConversation}
      />

      {/* Modal broadcast */}
      <BroadcastDialog
        open={broadcastOpen}
        onOpenChange={setBroadcastOpen}
        adminId={user?.uid}
        adminName={user?.displayName ?? user?.email ?? "Central"}
        onSent={loadChats}
      />
    </PageWithBack>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Nova conversa — seletor de prestador
// ─────────────────────────────────────────────────────────────────────────────

interface ProviderRow {
  id: string
  name: string
  email?: string
  active?: boolean
}

function NewConversationDialog({
  open,
  onOpenChange,
  onPick,
}: {
  open: boolean
  onOpenChange: (v: boolean) => void
  onPick: (p: ProviderRow) => void
}) {
  const [providers, setProviders] = useState<ProviderRow[]>([])
  const [search, setSearch] = useState("")
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    adminFetch("/api/provider-chats/directory?limit=200", { cache: "no-store" })
      .then((r) => r.json())
      .then((json) => {
        if (json.success) setProviders(json.providers as ProviderRow[])
      })
      .finally(() => setLoading(false))
  }, [open])

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return providers
    return providers.filter(
      (p) =>
        p.name.toLowerCase().includes(q) ||
        (p.email ?? "").toLowerCase().includes(q) ||
        p.id.toLowerCase().includes(q)
    )
  }, [providers, search])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Plus className="h-5 w-5 text-primary" />
            Nova conversa com prestador
          </DialogTitle>
          <DialogDescription>Escolha um prestador para iniciar o atendimento.</DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div className="relative">
            <Search className="h-4 w-4 absolute left-2.5 top-2.5 text-muted-foreground" />
            <Input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar prestador por nome ou email..."
              className="pl-8"
            />
          </div>
          <ScrollArea className="h-72 rounded-md border">
            {loading ? (
              <p className="p-4 text-center text-sm text-muted-foreground">Carregando prestadores…</p>
            ) : filtered.length === 0 ? (
              <p className="p-4 text-center text-sm text-muted-foreground">Nenhum prestador encontrado.</p>
            ) : (
              <ul className="divide-y divide-border">
                {filtered.map((p) => (
                  <li key={p.id}>
                    <button
                      type="button"
                      onClick={() => onPick(p)}
                      className="flex w-full items-center justify-between gap-2 px-3 py-2.5 text-left hover:bg-muted"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium">{p.name}</p>
                        <p className="truncate text-xs text-muted-foreground">{p.email || p.id}</p>
                      </div>
                      {p.active === false && (
                        <Badge variant="outline" className="shrink-0 text-[10px]">
                          inativo
                        </Badge>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </ScrollArea>
        </div>
      </DialogContent>
    </Dialog>
  )
}

// ─────────────────────────────────────────────────────────────────────────────
// Broadcast modal
// ─────────────────────────────────────────────────────────────────────────────

interface BroadcastProps {
  open: boolean
  onOpenChange: (v: boolean) => void
  adminId?: string
  adminName?: string
  onSent?: () => void
}

function BroadcastDialog({ open, onOpenChange, adminId, adminName, onSent }: BroadcastProps) {
  const [text, setText] = useState("")
  const [type, setType] = useState<"notice" | "promotion" | "text">("notice")
  const [audience, setAudience] = useState<"all" | "active">("active")
  const [sending, setSending] = useState(false)

  async function submit() {
    if (!text.trim()) return
    setSending(true)
    try {
      const res = await adminFetch("/api/provider-chats/broadcast", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          text: text.trim(),
          type,
          audience,
          adminId,
          adminName,
        }),
      })
      const json = await res.json()
      if (!json.success) {
        alert(json.error ?? "Erro ao enviar broadcast")
        return
      }
      alert(
        `Broadcast enviado para ${json.delivered} prestador(es).\n` +
          `FCM: ${json.fcm.sent} enviadas, ${json.fcm.failed} falharam.`
      )
      setText("")
      onOpenChange(false)
      onSent?.()
    } finally {
      setSending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Megaphone className="h-5 w-5 text-primary" />
            Broadcast para prestadores
          </DialogTitle>
          <DialogDescription>
            Envia a mesma mensagem para um grupo de prestadores ao mesmo tempo.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-3">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="text-xs font-medium text-muted-foreground">Audiência</label>
              <Select value={audience} onValueChange={(v) => setAudience(v as typeof audience)}>
                <SelectTrigger className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="active">Prestadores ativos</SelectItem>
                  <SelectItem value="all">Todos prestadores</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div>
              <label className="text-xs font-medium text-muted-foreground">Tipo</label>
              <Select value={type} onValueChange={(v) => setType(v as typeof type)}>
                <SelectTrigger className="mt-1">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="notice">Aviso</SelectItem>
                  <SelectItem value="promotion">Promoção</SelectItem>
                  <SelectItem value="text">Mensagem</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <div>
            <label className="text-xs font-medium text-muted-foreground">Mensagem</label>
            <Textarea
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="Conteúdo do broadcast..."
              rows={5}
              maxLength={2000}
              className="mt-1 resize-none"
            />
            <p className="text-[10px] text-muted-foreground mt-1">{text.length}/2000</p>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancelar
          </Button>
          <Button onClick={submit} disabled={sending || !text.trim()}>
            {sending ? "Enviando..." : "Enviar broadcast"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
