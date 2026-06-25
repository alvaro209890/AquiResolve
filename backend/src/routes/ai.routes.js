const express = require('express');

const { authenticateRequest } = require('../middlewares/auth');
const { classifyNiche } = require('../services/ai-classify.service');
const HttpError = require('../utils/http-error');
const logger = require('../utils/logger');

const router = express.Router();

const MAX_NICHES = 60;

// Toda a trilha de IA exige usuário autenticado (ID token Firebase), igual aos pagamentos.
router.use(authenticateRequest);

/**
 * POST /api/ai/classify
 * Body: { description: string, niches: string[] }
 * Retorna: { ok, niche: string|null, serviceType: string|null, confidence: number, message: string }
 *
 * Classifica a descrição do problema do cliente em UM nicho da lista enviada (catálogo do app).
 * A IA nunca decide preço nem cria pedido — só sugere o nicho para o app rotear.
 */
router.post('/classify', async (req, res, next) => {
  try {
    const description = String(req.body?.description ?? '').trim();
    const niches = Array.isArray(req.body?.niches)
      ? req.body.niches.map((n) => String(n).trim()).filter(Boolean).slice(0, MAX_NICHES)
      : [];

    if (!description) {
      throw new HttpError(400, 'Descreva o que aconteceu para o Hello.', { code: 'AI_EMPTY_DESCRIPTION' });
    }
    if (niches.length === 0) {
      throw new HttpError(400, 'Lista de nichos vazia.', { code: 'AI_EMPTY_NICHES' });
    }

    const result = await classifyNiche({ description, niches });

    return res.status(200).json({
      ok: true,
      niche: result.niche,
      serviceType: result.serviceType ?? null,
      confidence: result.confidence,
      message: result.message
    });
  } catch (error) {
    if (error.code === 'AI_NOT_CONFIGURED') {
      logger.warn('IA não configurada (GROQ_API_KEY ausente)', { requestId: req.requestId });
      return next(new HttpError(503, 'Hello indisponível no momento.', { code: 'AI_NOT_CONFIGURED' }));
    }
    if (error instanceof HttpError) {
      return next(error);
    }
    // Falha de rede/Groq → 502 amigável (o app cai no fallback de busca/lista).
    logger.error('Falha ao classificar via IA', { requestId: req.requestId, error: error.message });
    return next(new HttpError(502, 'Hello indisponível no momento.', { code: 'AI_UPSTREAM_ERROR' }));
  }
});

module.exports = router;
