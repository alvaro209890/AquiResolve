const express = require('express');

const router = express.Router();
const { expireOrders } = require('../services/order-expiration.service');
const { verifyFirebaseToken } = require('../config/firebase');
const logger = require('../utils/logger');

/**
 * POST /api/cron/expire-orders
 * 
 * Endpoint chamado por cron externo (cron-job.org, Render Cron, etc.)
 * Verifica e expira pedidos em "distributing" há mais de 1h30.
 * 
 * Protegido por chave secreta (CRON_SECRET) para evitar abuso.
 */
router.post('/expire-orders', async (req, res) => {
  const cronSecret = req.headers['x-cron-secret'] || req.body?.secret;

  // Validação básica: exigir CRON_SECRET do ambiente
  const expectedSecret = process.env.CRON_SECRET;
  if (expectedSecret && cronSecret !== expectedSecret) {
    logger.warn('Tentativa de acesso não autorizado ao cron', {
      ip: req.ip
    });
    return res.status(401).json({ ok: false, error: 'Unauthorized' });
  }

  try {
    const result = await expireOrders();
    res.status(200).json({
      ok: true,
      ...result,
      timestamp: new Date().toISOString()
    });
  } catch (err) {
    logger.error('Erro no cron de expiração', { error: err.message });
    res.status(500).json({
      ok: false,
      error: err.message,
      timestamp: new Date().toISOString()
    });
  }
});

/**
 * GET /api/cron/expire-orders/status
 * 
 * Retorna status do serviço de expiração
 */
router.get('/expire-orders/status', (req, res) => {
  res.status(200).json({
    ok: true,
    service: 'order-expiration',
    timeout: '90 minutes',
    statusesChecked: ['distributing', 'pending'],
    targetStatus: 'expired',
    timestamp: new Date().toISOString()
  });
});

module.exports = router;
