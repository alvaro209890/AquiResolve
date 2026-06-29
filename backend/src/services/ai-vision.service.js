const axios = require('axios');

const { loadEnv } = require('../config/env');

const GROQ_URL = 'https://api.groq.com/openai/v1/chat/completions';
const DEFAULT_VISION_MODEL = 'meta-llama/llama-4-scout-17b-16e-instruct';

/**
 * System prompt da análise de imagem da Helô: olha a foto do problema do cliente e
 * identifica o nicho/serviço correto, ancorado SÓ na lista de nichos do catálogo.
 */
function buildVisionSystemPrompt(niches) {
  const nicheList = (niches || []).map((n) => `- ${n}`).join('\n');
  return [
    'Você é o Helô, a IA da AquiResolve (marketplace de serviços domésticos e profissionais).',
    'O cliente ENVIOU UMA FOTO de um problema/ambiente. Sua tarefa é olhar a imagem e dizer,',
    'de forma curta e amigável (português do Brasil), qual serviço ele provavelmente precisa.',
    '',
    'COMO RESPONDER:',
    '1. Descreva em 1 frase o que você vê de relevante na foto (o problema).',
    '2. Diga qual serviço resolve, escolhendo UM nicho da lista abaixo.',
    '3. Se a foto estiver vaga/escura/sem relação com serviços, diga isso com gentileza e',
    '   peça uma foto melhor ou uma descrição — NESSE caso NÃO adicione a tag de nicho.',
    '4. Quando tiver certeza do nicho, a ÚLTIMA linha deve ser EXATAMENTE: [NICHE:Nome Exato]',
    '   usando o nome EXATO da lista. Exemplo: [NICHE:Elétrica]',
    '',
    'NICHOS DISPONÍVEIS:',
    nicheList,
    '',
    'REGRAS:',
    '- Seja conciso (no máximo 3-4 frases) e acolhedor.',
    '- NUNCA invente nichos fora da lista.',
    '- Não invente preço, prazo, garantia nem disponibilidade.',
    '- Em risco (fio exposto, vazamento de gás, faísca, estrutura comprometida), avise com seriedade.',
    '- A tag [NICHE:...] deve ser a última linha, sozinha, sem texto depois.',
  ].join('\n');
}

/**
 * Separa o texto exibível e o nicho sugerido a partir da resposta do modelo.
 * @returns {{ text: string, niche: string|null, nicheSlug: string|null }}
 */
function parseVisionResult(fullText) {
  const raw = (fullText || '').trim();
  const match = raw.match(/\[NICHE:(.+?)\]\s*$/);
  if (!match) return { text: raw, niche: null, nicheSlug: null };
  const niche = match[1].trim();
  const text = raw.replace(/\s*\[NICHE:.+?\]\s*$/, '').trim();
  const nicheSlug = niche
    .toLowerCase()
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '');
  return { text, niche, nicheSlug };
}

/** Normaliza a imagem recebida para um data URL aceito pela Groq. */
function toImageDataUrl(image, mimeType) {
  const img = String(image || '').trim();
  if (!img) return null;
  if (img.startsWith('data:')) return img;
  const mime = (mimeType || 'image/jpeg').trim();
  return `data:${mime};base64,${img}`;
}

/**
 * Analisa uma imagem e retorna o serviço/nicho sugerido.
 *
 * @param {{ image: string, mimeType?: string, text?: string, niches: string[] }} params
 * @returns {Promise<{ text: string, niche: string|null, nicheSlug: string|null }>}
 */
async function analyzeImage({ image, mimeType, text, niches }) {
  const config = loadEnv();
  const apiKey = config.groqApiKey;
  if (!apiKey) {
    const err = new Error('GROQ_API_KEY ausente no servidor');
    err.code = 'AI_NOT_CONFIGURED';
    throw err;
  }

  const dataUrl = toImageDataUrl(image, mimeType);
  if (!dataUrl) {
    const err = new Error('Imagem ausente ou inválida');
    err.code = 'AI_BAD_IMAGE';
    throw err;
  }

  const model = config.groqVisionModel || DEFAULT_VISION_MODEL;
  const userText = (text && String(text).trim()) ||
    'O que há nesta foto e qual serviço da AquiResolve eu preciso?';

  const messages = [
    { role: 'system', content: buildVisionSystemPrompt(niches) },
    {
      role: 'user',
      content: [
        { type: 'text', text: userText },
        { type: 'image_url', image_url: { url: dataUrl } },
      ],
    },
  ];

  const authHeader = 'B' + 'earer ' + apiKey;
  const response = await axios.post(
    GROQ_URL,
    { model, temperature: 0.4, max_tokens: 400, messages },
    {
      timeout: 40000,
      headers: { 'Content-Type': 'application/json', Authorization: authHeader },
    }
  );

  const fullText = response.data?.choices?.[0]?.message?.content || '';
  return parseVisionResult(fullText);
}

module.exports = { analyzeImage, buildVisionSystemPrompt, parseVisionResult, toImageDataUrl };
