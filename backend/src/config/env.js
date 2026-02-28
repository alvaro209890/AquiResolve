const dotenv = require('dotenv');

dotenv.config();

let cachedEnv = null;

function getEnv(name, options = {}) {
  const {
    required = true,
    defaultValue = undefined
  } = options;

  const rawValue = process.env[name];

  if (rawValue === undefined || rawValue === null || rawValue === '') {
    if (required) {
      throw new Error(`Missing required environment variable: ${name}`);
    }

    return defaultValue;
  }

  return rawValue;
}

function loadEnv() {
  if (cachedEnv) {
    return cachedEnv;
  }

  const port = Number.parseInt(getEnv('PORT', { required: false, defaultValue: '3000' }), 10);

  cachedEnv = {
    nodeEnv: getEnv('NODE_ENV', { required: false, defaultValue: 'development' }),
    port: Number.isNaN(port) ? 3000 : port,
    pagarmeBaseUrl: getEnv('PAGARME_BASE_URL', {
      required: false,
      defaultValue: 'https://api.pagar.me/core/v5'
    }),
    pagarmeSecretKey: getEnv('PAGARME_SECRET_KEY'),
    firebaseProjectId: getEnv('FIREBASE_PROJECT_ID'),
    firebaseClientEmail: getEnv('FIREBASE_CLIENT_EMAIL'),
    firebasePrivateKey: getEnv('FIREBASE_PRIVATE_KEY').replace(/\\n/g, '\n'),
    corsOrigin: getEnv('CORS_ORIGIN', { required: false, defaultValue: '*' })
  };

  return cachedEnv;
}

module.exports = {
  loadEnv
};
