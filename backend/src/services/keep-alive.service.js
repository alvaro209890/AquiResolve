const axios = require('axios');

const logger = require('../utils/logger');

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '');
}

function startKeepAlive(config) {
  if (!config.keepAliveEnabled) {
    logger.info('Keep-alive desabilitado');
    return null;
  }

  const baseUrl = normalizeBaseUrl(config.keepAliveUrl);

  if (!baseUrl) {
    logger.warn('Keep-alive habilitado, mas nenhuma URL foi configurada', {
      expectedEnv: ['KEEP_ALIVE_URL', 'RENDER_EXTERNAL_URL']
    });
    return null;
  }

  const targetUrl = `${baseUrl}/api/health`;
  const intervalMs = config.keepAliveIntervalMs;

  const ping = async () => {
    const startedAt = Date.now();

    try {
      const response = await axios.get(targetUrl, {
        timeout: Math.min(intervalMs, 15000),
        headers: {
          'User-Agent': 'loginapp-payments-backend/keep-alive'
        }
      });

      logger.info('Keep-alive ping concluido', {
        targetUrl,
        status: response.status,
        durationMs: Date.now() - startedAt
      });
    } catch (err) {
      logger.warn('Keep-alive ping falhou', {
        targetUrl,
        durationMs: Date.now() - startedAt,
        error: err.message
      });
    }
  };

  const timer = setInterval(ping, intervalMs);

  if (typeof timer.unref === 'function') {
    timer.unref();
  }

  logger.info('Keep-alive agendado', {
    targetUrl,
    intervalMs
  });

  const initialTimer = setTimeout(() => {
    ping().catch(() => {});
  }, 5000);

  if (typeof initialTimer.unref === 'function') {
    initialTimer.unref();
  }

  return timer;
}

module.exports = {
  startKeepAlive
};
