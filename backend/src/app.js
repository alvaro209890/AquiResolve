const crypto = require('crypto');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');
const rateLimit = require('express-rate-limit');

const healthRoutes = require('./routes/health.routes');
const paymentsRoutes = require('./routes/payments.routes');
const paymentsWebhookRoutes = require('./routes/payments-webhook.routes');
const cronRoutes = require('./routes/cron.routes');
const routeRoutes = require('./routes/route.routes');
const aiRoutes = require('./routes/ai.routes');
const aiChatRoutes = require('./routes/ai-chat.routes');
const chatNotifyRoutes = require('./routes/chat-notify.routes');
const { notFoundHandler, errorHandler } = require('./middlewares/error-handler');

const paymentLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minuto
  max: 10, // 10 requisições por minuto por IP
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: {
      code: 'RATE_LIMITED',
      message: 'Muitas requisições. Aguarde um momento e tente novamente.'
    }
  }
});

// A IA é mais cara/abusável que uma rota comum → limite por IP mais apertado.
const aiLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minuto
  max: 15, // 15 classificações por minuto por IP
  standardHeaders: true,
  legacyHeaders: false,
  message: {
    error: {
      code: 'RATE_LIMITED',
      message: 'Muitas solicitações ao assistente. Aguarde um momento.'
    }
  }
});

function createCorsOptions(config) {
  if (!config.corsOrigin || config.corsOrigin === '*') {
    return { origin: true };
  }

  const allowedOrigins = config.corsOrigin
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

  return {
    origin(origin, callback) {
      if (!origin || allowedOrigins.includes(origin)) {
        callback(null, true);
        return;
      }

      callback(new Error('Origin não permitida pelo CORS'));
    }
  };
}

function createApp({ config }) {
  const app = express();

  app.disable('x-powered-by');
  app.use(helmet());
  app.use(cors(createCorsOptions(config)));
  app.use(express.json({
    limit: '1mb',
    // Guarda o corpo cru para a validação HMAC do webhook Pagar.me
    // (a assinatura é calculada sobre os bytes originais, não o JSON re-serializado).
    verify: (req, _res, buf) => {
      req.rawBody = buf;
    }
  }));

  app.use((req, res, next) => {
    req.requestId = req.headers['x-request-id'] || crypto.randomUUID();
    res.setHeader('x-request-id', req.requestId);
    next();
  });

  morgan.token('request-id', (req) => req.requestId);
  app.use(
    morgan(':method :url :status :response-time ms req_id=:request-id', {
      skip: (req) => req.path === '/api/health'
    })
  );

  app.use('/api/health', healthRoutes);
  // Webhook ANTES do paymentLimiter: a Pagar.me pode mandar rajadas de eventos
  // legítimos e não pode levar 429 (a autenticidade é validada no controller).
  app.use('/api/payments/webhook', paymentsWebhookRoutes);
  app.use('/api/payments', paymentLimiter, paymentsRoutes);
  app.use('/api/route', routeRoutes);
  app.use('/api/ai', aiLimiter, aiRoutes);
  app.use('/api/ai', aiLimiter, aiChatRoutes);
  app.use('/api/chat-notify', chatNotifyRoutes);
  app.use('/api/cron', cronRoutes);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = {
  createApp
};
