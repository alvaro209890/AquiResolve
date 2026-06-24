const express = require('express');

const { authenticateRequest } = require('../middlewares/auth');
const { streamChat } = require('../services/ai-chat.service');
const logger = require('../utils/logger');

const router = express.Router();

// Toda rota de chat exige autenticação Firebase
router.use(authenticateRequest);

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
    // onDone — sinaliza fim do stream
    (fullText) => {
      if (!res.writableEnded) {
        res.write(`data: ${JSON.stringify({ done: true, fullText })}\n\n`);
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
        res.write(`data: ${JSON.stringify({ error: 'Assistente indisponível no momento.' })}\n\n`);
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
