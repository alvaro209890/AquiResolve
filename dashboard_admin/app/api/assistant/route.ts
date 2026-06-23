import { NextRequest, NextResponse } from 'next/server'
import { manualAsPromptContext } from '@/lib/manual-content'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// Copiloto IA do painel admin (plano 08). O admin pergunta "como faço X no painel?" dentro da
// aba Manual e a IA responde em passos, citando o caminho exato de menus/botões.
//
// Segurança: a chave Groq vive SÓ no servidor (env GROQ_API_KEY na Vercel) — nunca chega ao
// browser. Esta rota roda no servidor (Node) e é a única que fala com a Groq.
//
// Grounding: o contexto do prompt é o conteúdo REAL do Manual (lib/manual-content.ts), então a
// IA cita nomes verdadeiros de telas e não inventa fluxos inexistentes.

export const runtime = 'nodejs'

const GROQ_URL = 'https://api.groq.com/openai/v1/chat/completions'
// O id do modelo da Groq muda de tempos em tempos — configurável por env, com default atual.
const DEFAULT_MODEL = 'llama-3.3-70b-versatile'
const MAX_HISTORY = 6 // pares pergunta/resposta recentes (uso interno, poucos admins)

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

function buildSystemPrompt(): string {
  return [
    'Você é o Copiloto do Painel Administrativo da AquiResolve.',
    'Sua função é ensinar o administrador a USAR o painel, respondendo "onde clicar".',
    '',
    'REGRAS:',
    '- Responda SEMPRE em português do Brasil.',
    '- Responda em passos numerados curtos (1., 2., 3.), citando o caminho exato de menus/botões',
    '  (ex.: "Configurações → Parceiros AquiResolve → Novo parceiro").',
    '- Baseie-se EXCLUSIVAMENTE no conteúdo do Manual abaixo. Se a resposta não estiver no Manual,',
    '  diga que esse passo não está documentado e oriente procurar no Manual/suporte — NÃO invente telas.',
    '- Seja objetivo. Não exponha dados de clientes/pedidos; você só orienta o uso do painel.',
    '',
    '=== CONTEÚDO DO MANUAL (fonte da verdade) ===',
    manualAsPromptContext(),
  ].join('\n')
}

export async function POST(req: NextRequest) {
  try {
    await requireAdminPermission(req, 'dashboard')
  } catch (error: unknown) {
    return adminAuthorizationResponse(error) ?? NextResponse.json(
      { success: false, error: 'Falha ao validar acesso' },
      { status: 500 }
    )
  }

  let body: { question?: string; history?: ChatMessage[] }
  try {
    body = await req.json()
  } catch {
    return NextResponse.json({ success: false, error: 'Requisição inválida.' }, { status: 400 })
  }

  const question = String(body?.question ?? '').trim()
  if (!question) {
    return NextResponse.json({ success: false, error: 'Digite uma pergunta.' }, { status: 400 })
  }

  const apiKey = process.env.GROQ_API_KEY
  if (!apiKey) {
    return NextResponse.json(
      { success: false, error: 'O Copiloto ainda não foi configurado (GROQ_API_KEY ausente no servidor).' },
      { status: 503 }
    )
  }

  // Histórico curto e sanitizado (só user/assistant), para a IA manter o fio da conversa.
  const history: ChatMessage[] = Array.isArray(body?.history)
    ? body.history
        .filter((m) => m && (m.role === 'user' || m.role === 'assistant') && typeof m.content === 'string')
        .slice(-MAX_HISTORY * 2)
        .map((m) => ({ role: m.role, content: String(m.content).slice(0, 4000) }))
    : []

  const model = process.env.GROQ_MODEL || DEFAULT_MODEL

  const messages = [
    { role: 'system', content: buildSystemPrompt() },
    ...history,
    { role: 'user', content: question.slice(0, 4000) },
  ]

  try {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 20000)

    const groqRes = await fetch(GROQ_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        temperature: 0.2,
        max_tokens: 700,
        messages,
      }),
      signal: controller.signal,
    }).finally(() => clearTimeout(timeout))

    if (!groqRes.ok) {
      const detail = await groqRes.text().catch(() => '')
      console.error('[assistant] Groq respondeu', groqRes.status, detail.slice(0, 300))
      return NextResponse.json(
        { success: false, error: 'O Copiloto está indisponível no momento. Tente novamente em instantes.' },
        { status: 502 }
      )
    }

    const data = await groqRes.json()
    const answer = data?.choices?.[0]?.message?.content?.trim()
    if (!answer) {
      return NextResponse.json(
        { success: false, error: 'Não consegui gerar uma resposta agora. Tente reformular a pergunta.' },
        { status: 502 }
      )
    }

    return NextResponse.json({ success: true, answer })
  } catch (err) {
    const aborted = err instanceof Error && err.name === 'AbortError'
    console.error('[assistant] falha ao chamar Groq:', err)
    return NextResponse.json(
      {
        success: false,
        error: aborted
          ? 'O Copiloto demorou para responder. Tente novamente.'
          : 'Erro ao contatar o Copiloto. Tente novamente.',
      },
      { status: aborted ? 504 : 500 }
    )
  }
}
