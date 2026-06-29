const express = require('express');

const { authenticateRequest } = require('../middlewares/auth');
const { streamChat } = require('../services/ai-chat.service');
const { analyzeImage } = require('../services/ai-vision.service');
const logger = require('../utils/logger');

const router = express.Router();

// Toda rota de chat exige autenticação Firebase
router.use(authenticateRequest);

/**
 * POST /api/ai/vision
 * Body: { image: "<base64|dataURL>", mimeType?: string, text?: string, niches: string[] }
 * Resposta: { text, niche, nicheSlug }
 *
 * A Helô olha a FOTO do problema do cliente e identifica o serviço/nicho (Groq vision).
 * Não-streaming (uma resposta só). Imagem nunca é persistida no servidor.
 */
router.post('/vision', async (req, res) => {
  const image = req.body?.image;
  const mimeType = req.body?.mimeType;
  const text = req.body?.text;
  const niches = Array.isArray(req.body?.niches)
    ? req.body.niches.map((n) => String(n).trim()).filter(Boolean).slice(0, 60)
    : [];

  if (!image || typeof image !== 'string') {
    return res.status(400).json({ error: 'Envie uma imagem.' });
  }
  if (niches.length === 0) {
    return res.status(400).json({ error: 'Lista de nichos vazia.' });
  }
  // Guarda de tamanho: base64 ~1.37x os bytes; limita a ~8MB de payload de imagem.
  if (image.length > 8 * 1024 * 1024) {
    return res.status(413).json({ error: 'Imagem muito grande. Tente uma foto menor.' });
  }

  try {
    const result = await analyzeImage({ image, mimeType, text, niches });
    return res.json(result);
  } catch (error) {
    logger.error('Erro na análise de imagem da Helô', {
      error: error.message,
      code: error.code,
    });
    const status = error.code === 'AI_NOT_CONFIGURED' ? 503 : 502;
    return res.status(status).json({ error: 'Não consegui analisar a imagem agora.' });
  }
});

/**
 * POST /api/ai/chat
 * Body: { messages: [{role, content}], niches: string[] }
 * Response: SSE stream (text/event-stream)
 *   data: {"token": "palavra"}
 *   data: {"done": true, "fullText": "..."}
 *   data: {"error": "mensagem"}
 *
 * Chat multi-turno com streaming token-por-token via Groq (plano 06 v2).
 * Mantém contexto via histórico de mensagens no prompt.
 */
router.post('/chat', async (req, res) => {
  const messages = Array.isArray(req.body?.messages) ? req.body.messages : [];
  const niches = Array.isArray(req.body?.niches)
    ? req.body.niches.map((n) => String(n).trim()).filter(Boolean).slice(0, 60)
    : [];

  if (messages.length === 0) {
    return res.status(400).json({ error: 'Envie pelo menos uma mensagem.' });
  }
  if (niches.length === 0) {
    return res.status(400).json({ error: 'Lista de nichos vazia.' });
  }

  // Configura headers SSE
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });

  const userName = req.user?.name || req.user?.displayName || null;

  streamChat(
    { messages, niches, userName },
    // onToken — envia cada token como evento SSE
    (token) => {
      if (!res.writableEnded) {
        res.write(`data: ${JSON.stringify({ token })}\n\n`);
      }
    },
    // onDone — sinaliza fim do stream, extrai niche se presente
    (fullText) => {
      if (!res.writableEnded) {
        // Extrai tag [NICHE:Nome] da resposta antes de enviar
        const nicheMatch = fullText.match(/\[NICHE:(.+?)\]\s*$/);
        let displayText = fullText;
        let suggestedNiche = null;
        if (nicheMatch) {
          suggestedNiche = nicheMatch[1].trim();
          // Remove a tag do texto exibido
          displayText = fullText.replace(/\s*\[NICHE:.+?\]\s*$/, '').trim();
        }
        const donePayload = { done: true, fullText: displayText };
        if (suggestedNiche) {
          donePayload.niche = suggestedNiche;
          // Busca o slug do nicho para o app usar como service_category_name
          const nicheSlug = suggestedNiche
            .toLowerCase()
            .normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/[^a-z0-9]+/g, '-')
            .replace(/^-|-$/g, '');
          donePayload.nicheSlug = nicheSlug;
        }
        res.write(`data: ${JSON.stringify(donePayload)}\n\n`);
        res.end();
      }
    },
    // onError — erro tratado, nunca derruba o servidor
    (error) => {
      logger.error('Erro no streaming do chat IA', {
        error: error.message,
        code: error.code,
      });
      if (!res.writableEnded) {
        res.write(`data: ${JSON.stringify({ error: 'Helô indisponível no momento.' })}\n\n`);
        res.end();
      }
    }
  );

  // Timeout de segurança — 45 segundos máximo para o stream
  const timeout = setTimeout(() => {
    if (!res.writableEnded) {
      res.write(`data: ${JSON.stringify({ error: 'Tempo limite excedido.' })}\n\n`);
      res.end();
    }
  }, 45000);

  res.on('close', () => clearTimeout(timeout));
});

module.exports = router;
