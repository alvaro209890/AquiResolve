const axios = require('axios');

const { loadEnv } = require('../config/env');
const logger = require('../utils/logger');

// Proxy de classificação por IA (plano 06). O app envia a descrição do problema do cliente
// ("minha pia está vazando") + a lista de nichos do catálogo; a IA escolhe UM nicho da lista.
//
// A chave da Groq vive SÓ aqui no backend (env GROQ_API_KEY no Render) — nunca no APK.
// Mesmo padrão do proxy /api/route (OSRM): o app fala só com o nosso backend.

const GROQ_URL = 'https://api.groq.com/openai/v1/chat/completions';
// O id do modelo da Groq muda de tempos em tempos — configurável por env, com default atual.
const DEFAULT_MODEL = 'llama-3.3-70b-versatile';

function buildSystemPrompt(niches) {
  const list = niches.map((n) => `- ${n}`).join('\n');
  return [
    'Você é o assistente virtual da AquiResolve, um marketplace de serviços domésticos e profissionais no Brasil.',
    'Um cliente vai descrever, com as próprias palavras — muitas vezes FALANDO por voz, com gírias regionais, erros de português ou frases indiretas — um problema ou uma necessidade.',
    'Sua tarefa: entender a INTENÇÃO REAL do cliente e escolher EXATAMENTE UM nicho da lista abaixo que melhor resolve o caso.',
    '',
    'NICHOS DISPONÍVEIS (escolha só destes; nunca invente):',
    list,
    '',
    'Como interpretar:',
    '- Considere sinônimos, fala coloquial e descrições indiretas. Exemplos: "tá pingando água do teto"/"a pia entupiu" = hidráulica; "a tomada soltou faísca"/"acabou a luz só na minha casa" = elétrica; "preciso dar uma geral na casa" = limpeza/faxina; "o portão não abre" = automação/serralheria.',
    '- Se houver mais de um problema, escolha o PRINCIPAL ou o mais urgente.',
    '- Se for claramente uma emergência (vazamento forte, cheiro de gás, curto), deixe a "message" tranquilizadora e ágil.',
    '',
    'Responda SOMENTE com um JSON válido, sem nenhum texto fora dele, no formato:',
    '{"niche": <um nicho EXATAMENTE como na lista, ou null>, "serviceType": <nome curto do serviço específico mais provável em pt-BR, ou null>, "confidence": <número de 0 a 1>, "message": <frase curta e calorosa em pt-BR>}',
    '',
    'Regras:',
    '- "niche" precisa ser IDÊNTICO a um item da lista (mesma grafia). Se nada se encaixar, use null e explique gentilmente na "message".',
    '- "serviceType" é um PALPITE do serviço específico dentro do nicho (ex.: "Desentupimento de pia", "Instalação de tomada"). Se não tiver certeza, use null. Nunca invente um nicho aqui.',
    '- "message" deve ser curta (1 a 2 frases), acolhedora e em pt-BR (ex.: "Parece um problema hidráulico — posso te levar para Encanador?").',
    '- "confidence" reflete o quão certo você está do nicho (1 = certeza total).'
  ].join('\n');
}

/**
 * Classifica a descrição do cliente em um dos nichos fornecidos.
 * Nunca lança para o chamador de forma não tratada: erros viram { error }.
 *
 * @returns {Promise<{ niche: string|null, confidence: number, message: string }>}
 */
async function classifyNiche({ description, niches }) {
  const config = loadEnv();
  const apiKey = config.groqApiKey;

  if (!apiKey) {
    const err = new Error('GROQ_API_KEY ausente no servidor');
    err.code = 'AI_NOT_CONFIGURED';
    throw err;
  }

  const model = config.groqModel || DEFAULT_MODEL;

  const { data } = await axios.post(
    GROQ_URL,
    {
      model,
      temperature: 0,
      max_tokens: 250,
      response_format: { type: 'json_object' },
      messages: [
        { role: 'system', content: buildSystemPrompt(niches) },
        { role: 'user', content: String(description).slice(0, 1000) }
      ]
    },
    {
      timeout: 15000,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`
      }
    }
  );

  const raw = data?.choices?.[0]?.message?.content?.trim() || '';

  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (err) {
    logger.warn('IA devolveu JSON inválido', { raw: raw.slice(0, 200) });
    return { niche: null, confidence: 0, message: 'Não consegui identificar agora. Quer ver todos os serviços?' };
  }

  // Validação anti-alucinação: o nicho retornado precisa existir EXATAMENTE na lista enviada.
  const match = niches.find((n) => n.toLowerCase() === String(parsed?.niche ?? '').trim().toLowerCase());
  const niche = match || null;

  let confidence = Number(parsed?.confidence);
  if (!Number.isFinite(confidence)) confidence = niche ? 0.5 : 0;
  confidence = Math.max(0, Math.min(1, confidence));
  if (!niche) confidence = 0;

  // Serviço específico é só um PALPITE (o app o casa contra o catálogo real; se não casar, é ignorado).
  // Só faz sentido quando há nicho.
  let serviceType = null;
  if (niche && typeof parsed?.serviceType === 'string') {
    const t = parsed.serviceType.trim();
    if (t && t.toLowerCase() !== 'null') serviceType = t.slice(0, 80);
  }

  const message =
    typeof parsed?.message === 'string' && parsed.message.trim()
      ? parsed.message.trim()
      : niche
        ? `Acho que é um caso de ${niche}. Posso te direcionar?`
        : 'Não consegui identificar o serviço. Quer ver todos os serviços?';

  return { niche, serviceType, confidence, message };
}

module.exports = {
  classifyNiche
};
