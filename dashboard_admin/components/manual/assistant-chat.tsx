"use client"

import { useRef, useState } from "react"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Button } from "@/components/ui/button"
import { adminFetch } from "@/lib/admin-api"
import { Textarea } from "@/components/ui/textarea"
import { Bot, Send, User, Loader2 } from "lucide-react"

// Copiloto IA do painel (plano 08). Widget de chat no topo da aba Manual: o admin pergunta
// "como faço X?" e recebe passos com onde clicar. Fala apenas com a rota /api/assistant
// (a chave Groq fica no servidor). Histórico curto vive só na memória do componente.

interface ChatMessage {
  role: "user" | "assistant"
  content: string
}

const EXAMPLES = [
  "Como cadastro um parceiro?",
  "Como crio um banner na Home?",
  "Como reembolso um pedido?",
  "Como configuro o cashback?",
]

export function AssistantChat() {
  const [question, setQuestion] = useState("")
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const listRef = useRef<HTMLDivElement>(null)

  async function ask(text: string) {
    const q = text.trim()
    if (!q || loading) return

    setError(null)
    const nextMessages: ChatMessage[] = [...messages, { role: "user", content: q }]
    setMessages(nextMessages)
    setQuestion("")
    setLoading(true)
    // Rola para o fim assim que a pergunta entra.
    requestAnimationFrame(() => listRef.current?.scrollTo({ top: listRef.current.scrollHeight }))

    try {
      const res = await adminFetch("/api/assistant", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          question: q,
          // Envia só o histórico anterior (sem a pergunta atual, já incluída no body).
          history: messages.slice(-12),
        }),
      })
      const data = await res.json()
      if (data?.success && data.answer) {
        setMessages((prev) => [...prev, { role: "assistant", content: data.answer }])
      } else {
        setError(data?.error || "Não foi possível obter a resposta.")
      }
    } catch {
      setError("Falha de conexão com o Copiloto. Tente novamente.")
    } finally {
      setLoading(false)
      requestAnimationFrame(() => listRef.current?.scrollTo({ top: listRef.current.scrollHeight }))
    }
  }

  function onKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    // Enter envia; Shift+Enter quebra linha.
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      ask(question)
    }
  }

  return (
    <Card className="border-indigo-200 bg-indigo-50/40">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg">
          <span className="rounded-md bg-indigo-100 p-1.5 text-indigo-700">
            <Bot className="h-5 w-5" />
          </span>
          Pergunte ao Copiloto do Painel
        </CardTitle>
        <p className="text-sm text-muted-foreground">
          Descreva o que quer fazer e o Copiloto responde com os passos e onde clicar. As respostas
          são baseadas neste Manual.
        </p>
      </CardHeader>
      <CardContent className="space-y-3">
        {/* Conversa */}
        {messages.length > 0 && (
          <div
            ref={listRef}
            className="max-h-80 space-y-3 overflow-y-auto rounded-lg border bg-white p-3"
          >
            {messages.map((m, i) => (
              <div key={i} className="flex gap-2">
                <div
                  className={`mt-0.5 flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${
                    m.role === "user" ? "bg-muted text-foreground" : "bg-indigo-100 text-indigo-700"
                  }`}
                >
                  {m.role === "user" ? <User className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
                </div>
                <div className="whitespace-pre-wrap rounded-lg bg-muted/50 px-3 py-2 text-sm leading-relaxed">
                  {m.content}
                </div>
              </div>
            ))}
            {loading && (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                Pensando…
              </div>
            )}
          </div>
        )}

        {/* Exemplos clicáveis (só quando a conversa está vazia) */}
        {messages.length === 0 && (
          <div className="flex flex-wrap gap-2">
            {EXAMPLES.map((ex) => (
              <button
                key={ex}
                type="button"
                onClick={() => ask(ex)}
                disabled={loading}
                className="rounded-full border border-indigo-200 bg-white px-3 py-1 text-xs text-indigo-700 transition hover:bg-indigo-100 disabled:opacity-50"
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
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Pergunte como fazer algo no painel… (Enter envia, Shift+Enter quebra linha)"
            rows={2}
            disabled={loading}
            className="resize-none bg-white"
          />
          <Button
            type="button"
            onClick={() => ask(question)}
            disabled={loading || !question.trim()}
            className="shrink-0"
          >
            {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Send className="h-4 w-4" />}
            <span className="ml-1.5 hidden sm:inline">Enviar</span>
          </Button>
        </div>
      </CardContent>
    </Card>
  )
}
