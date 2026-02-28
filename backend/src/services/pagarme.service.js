const axios = require('axios');

const { loadEnv } = require('../config/env');

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
  const response = await client.post('/orders', payload);
  return response.data;
}

async function getOrderStatus(orderId) {
  const client = buildClient();
  const response = await client.get(`/orders/${encodeURIComponent(orderId)}`);
  return response.data;
}

module.exports = {
  createOrder,
  getOrderStatus
};
