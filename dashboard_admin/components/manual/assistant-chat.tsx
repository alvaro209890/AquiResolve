"use client"

import { useEffect, useRef, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { adminFetch } from "@/lib/admin-api"
import { Textarea } from "@/components/ui/textarea"
import { Bot, Send, User, Loader2, Plus, Trash2 } from "lucide-react"

// Copiloto IA do painel (plano 08). Widget de chat no topo da aba Manual: o admin pergunta
// "como faço X?" e recebe passos com onde clicar. Histórico persiste na sessão (sessionStorage).

interface ChatMessage {
  role: "user" | "assistant"
  content: string
}

const SESSION_KEY = "aquiresolve_copiloto_history"

const EXAMPLES = [
  "Como cadastro um parceiro?",
  "Como reembolso um pedido?",
  "Como configuro o cashback?",
  "Como crio um banner na Home?",
  "Como reatribuo um pedido a outro prestador?",
  "Como aprovo um prestador?",
  "Como crio um novo administrador?",
  "Como envio mensagem em massa para clientes?",
]

function loadHistory(): ChatMessage[] {
  try {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (!raw) return []
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function saveHistory(msgs: ChatMessage[]) {
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify(msgs.slice(-40)))
  } catch {}
}

/** Renderiza texto simples com suporte básico a listas numeradas, bullets e negrito. */
function renderContent(text: string) {
  const lines = text.split("\n")
  const elements: React.ReactNode[] = []

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const trimmed = line.trim()

    if (!trimmed) {
      elements.push(<div key={i} className="h-2" />)
      continue
    }

    // Lista numerada: "1. texto" ou "1) texto"
    const numbered = trimmed.match(/^(\d+)[.)]\s+(.+)/)
    if (numbered) {
      elements.push(
        <div key={i} className="flex gap-2 text-sm leading-relaxed">
          <span className="shrink-0 font-semibold text-indigo-700 dark:text-indigo-300">{numbered[1]}.</span>
          <span>{applyInline(numbered[2])}</span>
        </div>
      )
      continue
    }

    // Bullet: "- texto" ou "• texto"
    const bullet = trimmed.match(/^[-•]\s+(.+)/)
    if (bullet) {
      elements.push(
        <div key={i} className="flex gap-2 text-sm leading-relaxed">
          <span className="shrink-0 text-indigo-500 dark:text-indigo-400">•</span>
          <span>{applyInline(bullet[1])}</span>
        </div>
      )
      continue
    }

    // Título: linha que começa com ### ou **texto**
    if (trimmed.startsWith("### ") || trimmed.startsWith("## ")) {
      const title = trimmed.replace(/^#+\s+/, "")
      elements.push(
        <p key={i} className="text-sm font-semibold text-foreground mt-1">{applyInline(title)}</p>
      )
      continue
    }

    elements.push(
      <p key={i} className="text-sm leading-relaxed">{applyInline(trimmed)}</p>
    )
  }

  return <div className="space-y-1">{elements}</div>
}

function applyInline(text: string): React.ReactNode {
  // Negrito: **texto** ou __texto__
  const parts = text.split(/(\*\*[^*]+\*\*|__[^_]+__)/)
  return parts.map((part, i) => {
    if (part.startsWith("**") && part.endsWith("**")) {
      return <strong key={i}>{part.slice(2, -2)}</strong>
    }
    if (part.startsWith("__") && part.endsWith("__")) {
      return <strong key={i}>{part.slice(2, -2)}</strong>
    }
    // Caminho de menu: "A → B → C" em destaque
    if (part.includes("→")) {
      return (
        <span key={i} className="font-medium text-indigo-700 dark:text-indigo-300 bg-indigo-50 dark:bg-indigo-900/40 px-1 rounded text-xs">
          {part}
        </span>
      )
    }
    return part
  })
}

export function AssistantChat() {
  const [question, setQuestion] = useState("")
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmClear, setConfirmClear] = useState(false)
  const listRef = useRef<HTMLDivElement>(null)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  // Carrega histórico da sessão ao montar
  useEffect(() => {
    setMessages(loadHistory())
  }, [])

  function scrollToBottom() {
    requestAnimationFrame(() =>
      listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: "smooth" })
    )
  }

  async function ask(text: string) {
    const q = text.trim()
    if (!q || loading) return

    setError(null)
    setConfirmClear(false)
    const nextMessages: ChatMessage[] = [...messages, { role: "user", content: q }]
    setMessages(nextMessages)
    setQuestion("")
    setLoading(true)
    scrollToBottom()

    try {
      const res = await adminFetch("/api/assistant", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          question: q,
          // Envia o histórico completo (sem a pergunta atual, já no body).
          history: messages.slice(-16),
        }),
      })
      const data = await res.json()
      if (data?.success && data.answer) {
        const updated = [...nextMessages, { role: "assistant" as const, content: data.answer }]
        setMessages(updated)
        saveHistory(updated)
      } else {
        setError(data?.error || "Não foi possível obter a resposta.")
      }
    } catch {
      setError("Falha de conexão com o Copiloto. Tente novamente.")
    } finally {
      setLoading(false)
      scrollToBottom()
    }
  }

  function clearChat() {
    if (!confirmClear) {
      setConfirmClear(true)
      return
    }
    setMessages([])
    saveHistory([])
    setError(null)
    setConfirmClear(false)
    textareaRef.current?.focus()
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      ask(question)
    }
    if (e.key === "Escape" && confirmClear) {
      setConfirmClear(false)
    }
  }

  const hasMessages = messages.length > 0

  return (
    <Card className="border-indigo-200 dark:border-indigo-800 bg-indigo-50/40 dark:bg-indigo-950/30">
      <CardHeader className="pb-3">
        <div className="flex items-start justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="rounded-md bg-indigo-100 dark:bg-indigo-900/60 p-1.5 text-indigo-700 dark:text-indigo-300">
              <Bot className="h-5 w-5" />
            </span>
            <CardTitle className="text-lg">Copiloto do Painel</CardTitle>
          </div>

          {/* Ações do chat */}
          <div className="flex items-center gap-1">
            {hasMessages && (
              <Button
                variant="ghost"
                size="sm"
                onClick={clearChat}
                title={confirmClear ? "Clique novamente para confirmar" : "Limpar conversa"}
                className={
                  confirmClear
                    ? "text-red-600 hover:text-red-700 hover:bg-red-50 dark:text-red-400 dark:hover:text-red-300 dark:hover:bg-red-950/40"
                    : "text-muted-foreground hover:text-foreground"
                }
              >
                <Trash2 className="h-4 w-4" />
                <span className="ml-1 hidden sm:inline text-xs">
                  {confirmClear ? "Confirmar?" : "Limpar"}
                </span>
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                setMessages([])
                saveHistory([])
                setError(null)
                setConfirmClear(false)
                textareaRef.current?.focus()
              }}
              title="Nova conversa"
              className="text-muted-foreground hover:text-foreground"
            >
              <Plus className="h-4 w-4" />
              <span className="ml-1 hidden sm:inline text-xs">Nova</span>
            </Button>
          </div>
        </div>
        <p className="text-sm text-muted-foreground">
          Descreva o que quer fazer e o Copiloto responde com os passos e onde clicar.
          As respostas são baseadas neste Manual.
        </p>
      </CardHeader>

      <CardContent className="space-y-3">
        {/* Conversa */}
        {hasMessages && (
          <div
            ref={listRef}
            className="max-h-[420px] space-y-4 overflow-y-auto rounded-lg border bg-white dark:bg-card border-border p-3 scroll-smooth"
          >
            {messages.map((m, i) => (
              <div key={i} className="flex gap-3">
                <div
                  className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${
                    m.role === "user"
                      ? "bg-muted text-foreground dark:bg-muted dark:text-muted-foreground"
                      : "bg-indigo-100 dark:bg-indigo-900/60 text-indigo-700 dark:text-indigo-300"
                  }`}
                >
                  {m.role === "user" ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
                </div>
                <div
                  className={`flex-1 rounded-lg px-3 py-2 text-sm ${
                    m.role === "user"
                      ? "bg-muted/60 dark:bg-muted/40"
                      : "bg-indigo-50/60 dark:bg-indigo-950/40 border border-indigo-100 dark:border-indigo-800/50"
                  }`}
                >
                  {m.role === "assistant" ? renderContent(m.content) : (
                    <p className="leading-relaxed">{m.content}</p>
                  )}
                </div>
              </div>
            ))}

            {loading && (
              <div className="flex items-center gap-3 pl-10 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin text-indigo-500" />
                Pensando…
              </div>
            )}
          </div>
        )}

        {/* Exemplos clicáveis (só quando conversa está vazia) */}
        {!hasMessages && !loading && (
          <div className="flex flex-wrap gap-2">
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => ask(ex)}
                className="rounded-full border border-indigo-200 dark:border-indigo-700 bg-white dark:bg-card px-3 py-1 text-xs text-indigo-700 dark:text-indigo-300 transition hover:bg-indigo-100 dark:hover:bg-indigo-900/40 disabled:opacity-50"
              >
                {ex}
              </button>
            ))}
          </div>
        )}

        {error && (
          <p className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">{error}</p>
        )}

        {/* Entrada */}
        <div className="flex items-end gap-2">
          <Textarea
            ref={textareaRef}
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Pergunte como fazer algo no painel… (Enter envia, Shift+Enter quebra linha)"
            rows={2}
            disabled={loading}
            className="resize-none bg-white dark:bg-input/30"
          />
          <Button
            type="button"
            onClick={() => ask(question)}
            disabled={loading || !question.trim()}
            className="shrink-0 bg-indigo-600 hover:bg-indigo-700 text-white"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            <span className="ml-1.5 hidden sm:inline">Enviar</span>
          </Button>
        </div>

        <p className="text-xs text-muted-foreground/70">
          O histórico desta conversa é mantido enquanto a aba estiver aberta.
        </p>
      </CardContent>
    </Card>
  )
}
