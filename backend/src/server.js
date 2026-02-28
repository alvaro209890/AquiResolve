const { loadEnv } = require('./config/env');
const { initializeFirebase } = require('./config/firebase');
const { createApp } = require('./app');
const { startKeepAlive } = require('./services/keep-alive.service');
const logger = require('./utils/logger');

const config = loadEnv();
initializeFirebase();

const app = createApp({ config });

app.listen(config.port, () => {
  logger.info('Payments backend iniciado', {
    port: config.port,
    nodeEnv: config.nodeEnv,
    keepAliveEnabled: config.keepAliveEnabled,
    keepAliveUrl: config.keepAliveUrl || null,
    keepAliveIntervalMs: config.keepAliveIntervalMs
  });
});

startKeepAlive(config);
