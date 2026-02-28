const axios = require('axios');

const { loadEnv } = require('../config/env');
const logger = require('../utils/logger');

function buildClient() {
  const config = loadEnv();

  return axios.create({
    baseURL: config.pagarmeBaseUrl.replace(/\/+$/, ''),
    timeout: 30000,
    headers: {
      Authorization: `Basic ${Buffer.from(`${config.pagarmeSecretKey}:`).toString('base64')}`,
      'Content-Type': 'application/json'
    }
  });
}

async function createOrder(payload) {
  const client = buildClient();
  const startedAt = Date.now();
  logger.info('Enviando createOrder para Pagar.me', {
    endpoint: '/orders',
    orderCode: payload?.items?.[0]?.code || null,
    itemCount: Array.isArray(payload?.items) ? payload.items.length : 0,
    paymentCount: Array.isArray(payload?.payments) ? payload.payments.length : 0
  });
  const response = await client.post('/orders', payload);
  logger.info('Resposta do createOrder recebida da Pagar.me', {
    endpoint: '/orders',
    status: response.status,
    orderId: response.data?.id || null,
    paymentStatus: response.data?.status || null,
    durationMs: Date.now() - startedAt
  });
  return response.data;
}

async function getOrderStatus(orderId) {
  const client = buildClient();
  const startedAt = Date.now();
  logger.info('Consultando status na Pagar.me', {
    endpoint: `/orders/${orderId}`,
    orderId
  });
  const response = await client.get(`/orders/${encodeURIComponent(orderId)}`);
  logger.info('Resposta do status recebida da Pagar.me', {
    endpoint: `/orders/${orderId}`,
    status: response.status,
    orderId: response.data?.id || orderId,
    paymentStatus: response.data?.status || null,
    durationMs: Date.now() - startedAt
  });
  return response.data;
}

module.exports = {
  createOrder,
  getOrderStatus
};
