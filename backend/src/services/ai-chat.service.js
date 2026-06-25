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
    'SUAS CAPACIDADES:',
    '- Sugerir o nicho/serviço mais adequado baseado na descrição do problema',
    '- Responder perguntas sobre como a plataforma funciona',
    '- Dar informações sobre tipos de serviço disponíveis',
    '- Explicar o processo de contratação (escolher serviço → agendar → pagar → prestador vai até você)',
    '',
    'NICHOS DISPONÍVEIS NO CATÁLOGO:',
    nicheList,
    '',
    'REGRAS IMPORTANTES:',
    '- Responda primeiro o que o cliente perguntou; depois, se couber, sugira o nicho/serviço e o próximo passo',
    '- Se o cliente descrever um problema, sugira o nicho e se ofereça para direcioná-lo',
    '- Se o cliente pedir para criar o pedido, oriente-o sobre como fazer',
    '- Responda SEMPRE em português do Brasil, de forma amigável, clara e acolhedora',
    '- Seja descontraído sem perder objetividade: tom de conversa, resposta útil e caminho claro',
    '- Seja conciso — no máximo 3-4 frases por resposta',
    '- Evite linguagem engessada, corporativa ou robótica',
    '- Em urgência, risco elétrico, cheiro de gás, vazamento forte, emergência ou prejuízo, seja direto e seguro; nada de brincadeiras',
    '- Não invente preço, prazo, disponibilidade de prestador, garantia ou política comercial',
    '- Se não souber algo, seja honesto e sugira ver a lista completa de serviços',
    '- NUNCA invente nichos que não estão na lista',
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
