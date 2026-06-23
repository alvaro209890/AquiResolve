"use client"

import { useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Textarea } from "@/components/ui/textarea"
import { Badge } from "@/components/ui/badge"
import { useToast } from "@/hooks/use-toast"
import { Bell, Send, Users, User, RefreshCw, CheckCircle } from "lucide-react"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import { db } from "@/lib/firebase"
import { collection, getDocs, query, where, limit } from "firebase/firestore"
import { adminFetch } from "@/lib/admin-api"

interface SentRecord {
  id: string
  title: string
  message: string
  target: string
  sent: number
  failed: number
  sentAt: Date
}

export default function NotificacoesPage() {
  const [title, setTitle] = useState("")
  const [message, setMessage] = useState("")
  const [target, setTarget] = useState<"all_clients" | "all_providers" | "all" | "specific">("all_clients")
  const [specificUid, setSpecificUid] = useState("")
  const [sending, setSending] = useState(false)
  const [history, setHistory] = useState<SentRecord[]>([])
  const { toast } = useToast()

  async function resolveTargetUserIds(): Promise<string[]> {
    if (!db) return []
    if (target === "specific") return specificUid.trim() ? [specificUid.trim()] : []

    const constraints = target === "all_clients"
      ? [where("userType", "in", ["client", "cliente"])]
      : target === "all_providers"
      ? [where("userType", "in", ["provider", "prestador"])]
      : []

    const q = constraints.length > 0
      ? query(collection(db, "users"), ...constraints, limit(500))
      : query(collection(db, "users"), limit(500))

    const snap = await getDocs(q)
    return snap.docs.map(d => d.id)
  }

  async function send() {
    if (!title.trim() || !message.trim()) {
      toast({ title: "Campos obrigatórios", description: "Preencha título e mensagem.", variant: "destructive" })
      return
    }
    if (target === "specific" && !specificUid.trim()) {
      toast({ title: "UID obrigatório", description: "Informe o UID do destinatário.", variant: "destructive" })
      return
    }

    setSending(true)
    try {
      const userIds = await resolveTargetUserIds()
      if (userIds.length === 0 && target !== "specific") {
        toast({ title: "Sem destinatários", description: "Nenhum usuário encontrado para o grupo selecionado.", variant: "destructive" })
        return
      }

      const res = await adminFetch("/api/notifications/send", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          title,
          message,
          userIds: target === "specific" ? undefined : userIds,
          userId: target === "specific" ? specificUid.trim() : undefined,
        }),
      })

      const data = await res.json()
      if (!data.success) throw new Error(data.error)

      const record: SentRecord = {
        id: Date.now().toString(),
        title,
        message,
        target: target === "all_clients" ? "Todos os clientes"
          : target === "all_providers" ? "Todos os prestadores"
          : target === "all" ? "Todos os usuários"
          : `UID: ${specificUid}`,
        sent: data.results?.sent ?? 0,
        failed: data.results?.failed ?? 0,
        sentAt: new Date(),
      }
      setHistory(prev => [record, ...prev])

      toast({
        title: "Notificação enviada",
        description: `${record.sent} enviada(s)${record.failed > 0 ? `, ${record.failed} falha(s)` : ""}`,
      })
      setTitle("")
      setMessage("")
    } catch (e: unknown) {
      toast({
        title: "Erro ao enviar",
        description: e instanceof Error ? e.message : String(e),
        variant: "destructive",
      })
    } finally {
      setSending(false)
    }
  }

  const targetLabel = {
    all_clients: "Todos os Clientes",
    all_providers: "Todos os Prestadores",
    all: "Todos os Usuários",
    specific: "Usuário Específico",
  }

  return (
    <div className="space-y-6 max-w-3xl">
      <div className="flex items-center gap-3">
        <div className="h-9 w-9 rounded-lg bg-primary/10 flex items-center justify-center">
          <Bell className="h-5 w-5 text-primary" />
        </div>
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Central de Notificações</h1>
          <p className="text-sm text-muted-foreground">Envie push notifications para clientes e prestadores</p>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Send className="h-4 w-4" /> Nova Notificação
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-1">
            <label className="text-sm font-medium">Destinatários</label>
            <Select value={target} onValueChange={(v: any) => setTarget(v)}>
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all_clients">
                  <div className="flex items-center gap-2"><Users className="h-4 w-4" /> Todos os Clientes</div>
                </SelectItem>
                <SelectItem value="all_providers">
                  <div className="flex items-center gap-2"><Users className="h-4 w-4" /> Todos os Prestadores</div>
                </SelectItem>
                <SelectItem value="all">
                  <div className="flex items-center gap-2"><Users className="h-4 w-4" /> Todos os Usuários</div>
                </SelectItem>
                <SelectItem value="specific">
                  <div className="flex items-center gap-2"><User className="h-4 w-4" /> Usuário Específico (UID)</div>
                </SelectItem>
              </SelectContent>
            </Select>
          </div>

          {target === "specific" && (
            <div className="space-y-1">
              <label className="text-sm font-medium">UID do Usuário</label>
              <Input
                placeholder="Cole o UID do Firebase aqui"
                value={specificUid}
                onChange={e => setSpecificUid(e.target.value)}
              />
            </div>
          )}

          <div className="space-y-1">
            <label className="text-sm font-medium">Título</label>
            <Input
              placeholder="Ex: Promoção especial AquiResolve!"
              value={title}
              onChange={e => setTitle(e.target.value)}
              maxLength={100}
            />
            <p className="text-xs text-muted-foreground text-right">{title.length}/100</p>
          </div>

          <div className="space-y-1">
            <label className="text-sm font-medium">Mensagem</label>
            <Textarea
              placeholder="Ex: Aproveite 10% de cashback em todos os serviços esta semana!"
              value={message}
              onChange={e => setMessage(e.target.value)}
              rows={3}
              maxLength={300}
            />
            <p className="text-xs text-muted-foreground text-right">{message.length}/300</p>
          </div>

          <Button onClick={send} disabled={sending} className="w-full gap-2">
            {sending ? <RefreshCw className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            {sending ? "Enviando…" : `Enviar para ${targetLabel[target]}`}
          </Button>
        </CardContent>
      </Card>

      {history.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Histórico desta sessão</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {history.map(r => (
              <div key={r.id} className="flex items-start justify-between gap-3 p-3 rounded-lg border">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <CheckCircle className="h-4 w-4 text-green-500 shrink-0" />
                    <span className="text-sm font-medium truncate">{r.title}</span>
                  </div>
                  <p className="text-xs text-muted-foreground mt-0.5 truncate">{r.message}</p>
                  <div className="flex items-center gap-2 mt-1">
                    <Badge variant="secondary" className="text-xs">{r.target}</Badge>
                    <span className="text-xs text-muted-foreground">
                      {r.sent} enviada(s) {r.failed > 0 && `• ${r.failed} falha(s)`}
                    </span>
                  </div>
                </div>
                <span className="text-xs text-muted-foreground shrink-0">
                  {r.sentAt.toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit" })}
                </span>
              </div>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
