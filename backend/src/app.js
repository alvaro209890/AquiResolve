const crypto = require('crypto');
const express = require('express');
const cors = require('cors');
const helmet = require('helmet');
const morgan = require('morgan');

const healthRoutes = require('./routes/health.routes');
const paymentsRoutes = require('./routes/payments.routes');
const cronRoutes = require('./routes/cron.routes');
const { notFoundHandler, errorHandler } = require('./middlewares/error-handler');

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
  app.use(express.json({ limit: '1mb' }));

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
  app.use('/api/payments', paymentsRoutes);
  app.use('/api/cron', cronRoutes);

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = {
  createApp
};
