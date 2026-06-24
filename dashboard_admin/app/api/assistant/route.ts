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
const DEFAULT_MODEL = 'llama-3.3-70b-versatile'
const MAX_HISTORY_PAIRS = 8 // últimos 8 pares pergunta/resposta enviados ao modelo

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

function buildSystemPrompt(): string {
  return [
    'Você é o Copiloto do Painel Administrativo da AquiResolve — um assistente especializado em ajudar os administradores a usar o painel.',
    '',
    '== SUA FUNÇÃO ==',
    'Ensinar o administrador COMO USAR o painel: onde clicar, qual menu acessar, qual botão usar, qual fluxo seguir.',
    'Você responde perguntas operacionais sobre o painel. Você NÃO executa ações — você instrui.',
    '',
    '== REGRAS OBRIGATÓRIAS ==',
    '1. Responda SEMPRE em português do Brasil, de forma clara e direta.',
    '2. Quando houver passos, use lista numerada (1., 2., 3.) citando o caminho EXATO de menus e botões,',
    '   por exemplo: "Controle → Chat com Clientes → Enviar em massa".',
    '3. Baseie-se EXCLUSIVAMENTE no Manual abaixo. Se a resposta não estiver no Manual, diga honestamente',
    '   que não há documentação sobre isso e oriente o usuário a contatar o suporte técnico.',
    '   NUNCA invente telas, botões ou fluxos que não existam no Manual.',
    '4. Considere o histórico da conversa: se o usuário fez uma pergunta anterior, suas respostas',
    '   anteriores já foram dadas — não repita o que já disse; continue de onde parou.',
    '5. Se a pergunta for vaga ("como faço isso?"), peça esclarecimento em uma linha antes de responder.',
    '6. Seja objetivo: prefira listas a parágrafos longos. Máximo de 400 palavras por resposta.',
    '7. Não exponha dados sensíveis de clientes, pedidos ou prestadores reais.',
    '8. Quando relevante, mencione alertas importantes (ex.: deploy manual necessário no Vercel após mudanças).',
    '',
    '== TÓPICOS QUE VOCÊ DOMINA ==',
    '- Catálogo de serviços e nichos (Serviços → Catálogo do App)',
    '- Combos promocionais e banners da Home',
    '- Parceiros AquiResolve',
    '- Cashback AquiCash (fases Launch/Growth, combos de categoria)',
    '- Monitoramento de pedidos ao vivo e reatribuição',
    '- Chat com clientes e com prestadores (incluindo broadcast)',
    '- Aprovação de prestadores e de especialidades',
    '- Notificações push',
    '- Gestão de usuários e de pedidos (cancelamento, reembolso)',
    '- Logs de auditoria',
    '- Área Master: criar/editar/remover admins e permissões',
    '- Infraestrutura: Firebase, Render e Vercel',
    '',
    '=== MANUAL DO PAINEL (fonte da verdade — use exclusivamente isto) ===',
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

  // Histórico sanitizado — só user/assistant, últimos MAX_HISTORY_PAIRS pares.
  const history: ChatMessage[] = Array.isArray(body?.history)
    ? body.history
        .filter((m) => m && (m.role === 'user' || m.role === 'assistant') && typeof m.content === 'string')
        .slice(-(MAX_HISTORY_PAIRS * 2))
        .map((m) => ({ role: m.role, content: String(m.content).slice(0, 6000) }))
    : []

  const model = process.env.GROQ_MODEL || DEFAULT_MODEL

  const messages = [
    { role: 'system', content: buildSystemPrompt() },
    ...history,
    { role: 'user', content: question.slice(0, 6000) },
  ]

  try {
    const controller = new AbortController()
    const timeout = setTimeout(() => controller.abort(), 25000)

    const groqRes = await fetch(GROQ_URL, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        temperature: 0.15,
        max_tokens: 1200,
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
