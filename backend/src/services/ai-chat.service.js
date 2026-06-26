const axios = require('axios');

const { loadEnv } = require('../config/env');
const logger = require('../utils/logger');

const GROQ_URL = 'https://api.groq.com/openai/v1/chat/completions';
const DEFAULT_MODEL = 'llama-3.3-70b-versatile';

/**
 * Constrói o system prompt para chat multi-turno com contexto do usuário.
 * @param {{ niches: string[], userName?: string, hasRecentOrders?: boolean }} context
 */
function buildSystemPrompt(context) {
  const nicheList = context.niches.map((n) => `- ${n}`).join('\n');
  let prompt = [
    'Você é o Hello, a IA mobile da AquiResolve, um marketplace de serviços domésticos e profissionais.',
    'Você conversa com clientes que precisam de ajuda para encontrar o serviço certo.',
    'Sua personalidade é leve, próxima e descontraída, como uma pessoa educada tentando resolver rápido. Use frases naturais como "beleza", "vamos resolver", "me conta" ou "sem problema" quando fizer sentido, sem exagerar e sem virar piada.',
    '',
    'SEU TRABALHO PRINCIPAL (FLUXO DE DIAGNÓSTICO):',
    '1. Quando o cliente descrever um problema, faça perguntas para entender melhor:',
    '   - O que exatamente está acontecendo?',
    '   - Onde? (cômodo, tipo de imóvel)',
    '   - Há quanto tempo?',
    '   - É urgente? Tem risco?',
    '2. Quando tiver informação suficiente, identifique o nicho correto na lista de NICHOS DISPONÍVEIS.',
    '3. Após sugerir o nicho, SEMPRE adicione EXATAMENTE esta linha no final da sua resposta:',
    '   [NICHE:Nome Exato do Nicho]',
    '   Exemplo: se for encanador, termine com "[NICHE:Encanador]"',
    '   Exemplo: se for eletricista, termine com "[NICHE:Eletricista]"',
    '   Exemplo: se for faxina, termine com "[NICHE:Faxina]"',
    '   Use o nome EXATO do nicho como está na lista, sem alterar.',
    '   Só adicione esta tag quando tiver certeza do nicho.',
    '',
    'NICHOS DISPONÍVEIS NO CATÁLOGO:',
    nicheList,
    '',
    'REGRAS IMPORTANTES:',
    '- Faça NO MÁXIMO 1-2 perguntas por resposta. Nunca faça um questionário longo.',
    '- Se o cliente descrever algo muito vago, pergunte mais detalhes antes de sugerir.',
    '- Se o cliente quiser conversar sobre outra coisa (como funciona a plataforma, preços, etc), responda naturalmente SEM a tag [NICHE:...].',
    '- Responda SEMPRE em português do Brasil, de forma amigável, clara e acolhedora.',
    '- Seja descontraído sem perder objetividade.',
    '- Seja conciso — no máximo 3-5 frases por resposta.',
    '- Evite linguagem engessada, corporativa ou robótica.',
    '- Em urgência, risco elétrico, cheiro de gás, vazamento forte, emergência ou prejuízo, seja direto e seguro; nada de brincadeiras.',
    '- Não invente preço, prazo, disponibilidade de prestador, garantia ou política comercial.',
    '- Se não souber algo, seja honesto e sugira ver a lista completa de serviços.',
    '- NUNCA invente nichos que não estão na lista.',
    '- A tag [NICHE:...] deve ser a ÚLTIMA linha, sozinha, sem mais texto depois.',
  ].join('\n');

  if (context.userName) {
    prompt += `\n\nCliente atual: ${context.userName}`;
  }
  if (context.hasRecentOrders) {
    prompt += '\nEste cliente já usou a plataforma antes.';
  }

  return prompt;
}

/**
 * Faz streaming da resposta da Groq token por token.
 *
 * @param {Object} params
 * @param {Array<{role: string, content: string}>} params.messages - histórico da conversa
 * @param {string[]} params.niches - lista de nichos do catálogo
 * @param {string|null} params.userName - nome do cliente (opcional)
 * @param {function(string): void} onToken - chamado a cada token
 * @param {function(string): void} onDone - chamado ao final com o texto completo
 * @param {function(Error): void} onError
 */
async function streamChat({ messages, niches, userName }, onToken, onDone, onError) {
  const config = loadEnv();
  const apiKey = config.groqApiKey;

  if (!apiKey) {
    const err = new Error('GROQ_API_KEY ausente no servidor');
    err.code = 'AI_NOT_CONFIGURED';
    onError(err);
    return;
  }

  const model = config.groqModel || DEFAULT_MODEL;

  const systemMsg = { role: 'system', content: buildSystemPrompt({ niches, userName }) };
  const allMessages = [systemMsg, ...messages];

  const authHeader = 'B' + 'earer ' + apiKey;

  try {
    const response = await axios.post(
      GROQ_URL,
      {
        model,
        temperature: 0.7,
        max_tokens: 600,
        stream: true,
        messages: allMessages,
      },
      {
        timeout: 30000,
        responseType: 'stream',
        headers: {
          'Content-Type': 'application/json',
          Authorization: authHeader,
        },
      }
    );

    let fullText = '';
    let buffer = '';

    response.data.on('data', (chunk) => {
      buffer += chunk.toString();
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed || !trimmed.startsWith('data: ')) continue;
        const data = trimmed.slice(6);
        if (data === '[DONE]') continue;

        try {
          const parsed = JSON.parse(data);
          const token = parsed?.choices?.[0]?.delta?.content || '';
          if (token) {
            fullText += token;
            onToken(token);
          }
        } catch (_) {
          // ignora linhas malformadas no stream da Groq
        }
      }
    });

    response.data.on('end', () => {
      onDone(fullText.trim());
    });

    response.data.on('error', (err) => {
      onError(err);
    });

  } catch (error) {
    onError(error);
  }
}

module.exports = { streamChat };
