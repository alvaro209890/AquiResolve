const { initializeFirebase } = require('../config/firebase');
const logger = require('../utils/logger');

// Precificação de GUINCHO (reboque): diferente dos demais serviços, o valor é
// taxa de saída + (R$/km × distância da ORIGEM até o DESTINO informados pelo
// cliente). O motorista recebe uma porcentagem do total.
//
// Config gerida pelo painel admin em `app_config/guincho` (Admin SDK). Aqui há
// fallback hardcoded para que a cobrança nunca quebre se o doc não existir.

const TOWING_CONFIG_TTL_MS = 60 * 1000;
let cachedConfig = null;

const DEFAULT_CONFIG = Object.freeze({
  enabled: true,
  baseFee: 180.0, // taxa de saída (R$)
  pricePerKm: 3.9, // R$/km
  providerPercent: 70, // % do total que o motorista recebe
  minKm: 0 // km mínimos cobrados (0 = cobra exatamente a distância)
});

// Categorias que disparam o cálculo por km.
const TOWING_CATEGORY_KEYS = ['guincho', 'reboque', 'towing'];

function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function isTowingCategory(category) {
  const value = normalizeText(category).toLowerCase();
  return TOWING_CATEGORY_KEYS.some((key) => value.includes(key));
}

function roundMoney(value) {
  return Math.round(Number(value) * 100) / 100;
}

function clampPercent(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return DEFAULT_CONFIG.providerPercent;
  return Math.min(100, Math.max(0, n));
}

function clearTowingCache() {
  cachedConfig = null;
}

/**
 * Lê `app_config/guincho` do Firestore (cache curto). NUNCA lança: em qualquer
 * falha (sem credencial, doc ausente, erro) devolve o DEFAULT_CONFIG.
 */
async function getTowingConfig() {
  if (cachedConfig && cachedConfig.expires > Date.now()) {
    return cachedConfig.value;
  }

  let value = { ...DEFAULT_CONFIG };
  try {
    const admin = initializeFirebase();
    if (admin.apps && admin.apps.length > 0) {
      const snap = await admin.firestore().collection('app_config').doc('guincho').get();
      if (snap.exists) {
        const d = snap.data() || {};
        value = {
          enabled: d.enabled !== false,
          baseFee: Number(d.baseFee) >= 0 ? Number(d.baseFee) : DEFAULT_CONFIG.baseFee,
          pricePerKm: Number(d.pricePerKm) >= 0 ? Number(d.pricePerKm) : DEFAULT_CONFIG.pricePerKm,
          providerPercent: clampPercent(d.providerPercent),
          minKm: Number(d.minKm) >= 0 ? Number(d.minKm) : DEFAULT_CONFIG.minKm
        };
      }
    }
  } catch (error) {
    logger.warn('Falha ao ler app_config/guincho; usando default', { error: error.message });
    value = { ...DEFAULT_CONFIG };
  }

  cachedConfig = { value, expires: Date.now() + TOWING_CONFIG_TTL_MS };
  return value;
}

/**
 * Calcula o preço de uma corrida de guincho a partir da distância (km) da
 * origem ao destino. Retorna o mesmo shape de calculateServicePricing
 * (estimatedPrice / providerCommission) + a quebra do guincho.
 */
async function calculateTowingPricing({ category, serviceType, distanceKm }) {
  const config = await getTowingConfig();

  const rawKm = Number(distanceKm);
  const safeKm = Number.isFinite(rawKm) && rawKm > 0 ? rawKm : 0;
  const billableKm = Math.max(safeKm, Number(config.minKm) || 0);

  const distanceCost = billableKm * config.pricePerKm;
  const estimatedPrice = roundMoney(config.baseFee + distanceCost);
  const providerCommission = roundMoney((estimatedPrice * config.providerPercent) / 100);

  return {
    category: normalizeText(category) || 'Guincho',
    serviceType: normalizeText(serviceType) || 'Transporte de veículo',
    estimatedPrice,
    providerCommission,
    source: 'towing',
    towing: {
      baseFee: roundMoney(config.baseFee),
      pricePerKm: roundMoney(config.pricePerKm),
      distanceKm: roundMoney(billableKm),
      distanceCost: roundMoney(distanceCost),
      providerPercent: config.providerPercent
    }
  };
}

module.exports = {
  DEFAULT_TOWING_CONFIG: DEFAULT_CONFIG,
  isTowingCategory,
  getTowingConfig,
  calculateTowingPricing,
  clearTowingCache
};
