const HttpError = require('../utils/http-error');
const { initializeFirebase } = require('../config/firebase');
const logger = require('../utils/logger');
const { isTowingCategory, calculateTowingPricing } = require('./towing-pricing.service');

// Cache em memória do lookup no Firestore (catalog_services) para não bater no banco
// a cada cálculo. TTL curto: mudanças no painel refletem em segundos.
const FIRESTORE_PRICING_TTL_MS = 60 * 1000;
const firestorePricingCache = new Map();

function clearPricingCache() {
  firestorePricingCache.clear();
}

// Fonte: app/src/main/java/com/aquiresolve/app/models/ServicePricing.kt
// Mantida no backend para que preço/repasse não sejam definidos pelo APK.
const pricingTable = {
  "Elétrica": {
    "Instalação de lâmpadas": [
      110.0,
      55.0
    ],
    "Instalação de tomada": [
      110.0,
      55.0
    ],
    "Troca de disjuntor": [
      150.0,
      75.0
    ],
    "Instalação de interruptor": [
      110.0,
      55.0
    ],
    "Instalação de chuveiro": [
      150.0,
      75.0
    ],
    "Instalação de resistência": [
      110.0,
      55.0
    ],
    "Instalação de luminária": [
      150.0,
      75.0
    ],
    "Instalação de spots": [
      110.0,
      55.0
    ],
    "Revisão Elétrica (até 7 pontos)": [
      200.0,
      100.0
    ]
  },
  "Encanador": {
    "Troca de torneira": [
      160.0,
      80.0
    ],
    "Troca de rabicho": [
      160.0,
      80.0
    ],
    "Troca de sifão": [
      110.0,
      55.0
    ],
    "Troca de Filtro": [
      160.0,
      80.0
    ],
    "Troca de reparos de registro": [
      160.0,
      80.0
    ],
    "Troca de reparos de torneira": [
      160.0,
      80.0
    ],
    "Troca kit de caixa acoplada": [
      160.0,
      80.0
    ],
    "Reparos de descarga de parede": [
      160.0,
      80.0
    ],
    "Revisão hidráulica (até 7 pontos)": [
      160.0,
      80.0
    ],
    "Vazamentos": [
      120.0,
      60.0
    ],
    "Troca de torneira monobloco": [
      260.0,
      130.0
    ]
  },
  "Instalação": {
    "Instalação de Suporte de tv": [
      160.0,
      80.0
    ],
    "Instalação de ventilador de teto": [
      190.0,
      95.0
    ],
    "Instalação de máquina de lavar": [
      190.0,
      95.0
    ],
    "Instalação de Lava louça": [
      190.0,
      95.0
    ],
    "Instalação de Fogão Cooktop": [
      180.0,
      90.0
    ],
    "Instalação de Purificador": [
      160.0,
      80.0
    ],
    "Conversão de gás para fogão cooktop": [
      130.0,
      65.0
    ],
    "Varal de teto": [
      150.0,
      75.0
    ]
  },
  "Caixa d'água": {
    "Limpeza de caixa d'água de 1000 litros": [
      150.0,
      75.0
    ],
    "Limpeza de caixa d'água de 2000 litros": [
      250.0,
      125.0
    ],
    "Limpeza de caixa d'água de 3000 litros": [
      350.0,
      175.0
    ],
    "Limpeza de caixa d'água de 4000 litros": [
      450.0,
      225.0
    ],
    "Limpeza de caixa d'água de 5000 litros": [
      550.0,
      275.0
    ],
    "Troca de boia": [
      150.0,
      75.0
    ]
  },
  "Desentupimento manual": {
    "Desentupimento de pia": [
      180.0,
      90.0
    ],
    "Desentupimento ralo": [
      180.0,
      90.0
    ],
    "Desentupimento vaso": [
      180.0,
      90.0
    ]
  },
  "Desentupimento com maquinário": {
    "Até 2 metros": [
      200.0,
      100.0
    ],
    "Adicional por Metro": [
      90.0,
      45.0
    ]
  },
  "Caça-vazamentos": {
    "Caça-vazamentos": [
      550.0,
      385.0
    ]
  },
  "Limpeza de estofados": {
    "Limpeza de sofá 2 lugares": [
      215.0,
      129.0
    ],
    "Limpeza de sofá 3 lugares": [
      265.0,
      159.0
    ],
    "Limpeza de sofá retrátil": [
      265.0,
      159.0
    ],
    "Limpeza de sofá de canto": [
      265.0,
      159.0
    ],
    "Limpeza de poltronas estofadas": [
      195.0,
      117.0
    ],
    "Limpeza de tapetes pequenos (até 2 mts)": [
      215.0,
      129.0
    ],
    "Limpeza de cadeiras estofadas": [
      195.0,
      117.0
    ],
    "Limpeza de carpetes pequenos (até 2mts)": [
      215.0,
      129.0
    ],
    "Higienização de colchões Casal": [
      215.0,
      129.0
    ],
    "Colchão solteiro": [
      145.0,
      87.0
    ],
    "Colchão king": [
      315.0,
      189.0
    ],
    "Colchão queen": [
      265.0,
      159.0
    ],
    "Impermeabilização": [
      65.0,
      39.0
    ]
  },
  "Eletrodomésticos": {
    "Conserto de micro-ondas": [
      160.0,
      80.0
    ],
    "Reparo de fogão e forno": [
      160.0,
      80.0
    ],
    "Reparo de pequenos eletrodomésticos": [
      160.0,
      80.0
    ],
    "Instalação de eletrodomésticos": [
      190.0,
      95.0
    ],
    "Geladeira e freezer": [
      250.0,
      125.0
    ],
    "Máquina de lavar": [
      180.0,
      90.0
    ]
  },
  "Chaveiro residencial": {
    "Abertura de portas residencial": [
      180.0,
      108.0
    ],
    "Ajuste de fechaduras": [
      180.0,
      108.0
    ],
    "Instalação de fechadura eletrônica e digital": [
      280.0,
      168.0
    ],
    "Extração de chave": [
      150.0,
      90.0
    ]
  },
  "Serviços automotivos": {
    "Abertura de portas de veículos": [
      180.0,
      90.0
    ],
    "Extração de chaves quebradas": [
      180.0,
      90.0
    ],
    "Remendo de pneu": [
      80.0,
      40.0
    ],
    "Remendo de pneu Caminhonete, SUV e vans": [
      115.0,
      57.5
    ],
    "Troca de pneu no local": [
      85.0,
      42.5
    ],
    "Troca de pneu Caminhonete, SUV e vans": [
      115.0,
      57.5
    ],
    "Pane seca (entrega de combustível)": [
      85.0,
      42.5
    ],
    "Partida elétrica": [
      120.0,
      60.0
    ]
  },
  "Montagem de móveis": {
    "Guarda roupas": [
      0.0,
      100.0
    ],
    "Cama": [
      0.0,
      90.0
    ],
    "Mesa": [
      0.0,
      75.0
    ],
    "Cômoda": [
      0.0,
      75.0
    ],
    "Armário": [
      0.0,
      75.0
    ],
    "Escrivaninha": [
      0.0,
      75.0
    ],
    "Prateleiras": [
      0.0,
      65.0
    ],
    "Objetos de cozinha": [
      0.0,
      65.0
    ],
    "Objetos de banheiro": [
      0.0,
      65.0
    ]
  },
  "Faxina": {
    "Faxina Básica (apt pequeno 1 a 2 quartos) - 4h a 5h": [
      190.0,
      133.0
    ],
    "Faxina completa (apt/casa média 2 a 3 quartos) - 6h a 8h": [
      250.0,
      175.0
    ],
    "Faxina pesada (casa grande, pós-obra, mudança) - 10h": [
      450.0,
      315.0
    ]
  },
  "Ar condicionado": {
    "9 a 12 mil BTUs split": [
      650.0,
      364.0
    ],
    "18 a 30 mil BTUs": [
      750.0,
      420.0
    ],
    "Ar de janela": [
      220.0,
      123.2
    ],
    "Higienização de 9 a 30 mil BTUs": [
      300.0,
      168.0
    ]
  }
};

function normalizeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeCategory(category) {
  const value = normalizeText(category).toLowerCase();
  if (value === 'hidráulica' || value === 'hidraulica' || value === 'encanador') return 'Encanador';
  if (value === 'estofados' || value === 'limpeza de estofados') return 'Limpeza de estofados';
  return normalizeText(category);
}

function deriveClientPriceFromProvider(providerValue) {
  const value = Number(providerValue);
  if (!Number.isFinite(value) || value <= 0) return null;
  return Math.round(value * 200) / 100;
}

function getPair(category, serviceType) {
  const normalizedCategory = normalizeCategory(category);
  const services = pricingTable[normalizedCategory];
  const requestedService = normalizeText(serviceType);

  if (services) {
    if (Object.prototype.hasOwnProperty.call(services, requestedService)) {
      return services[requestedService];
    }
    const lowered = requestedService.toLowerCase();
    const key = Object.keys(services).find((service) => service.toLowerCase() === lowered);
    if (key) return services[key];
  }

  const lowered = requestedService.toLowerCase();
  for (const serviceMap of Object.values(pricingTable)) {
    const key = Object.keys(serviceMap).find((service) => service.toLowerCase() === lowered);
    if (key) return serviceMap[key];
  }

  return null;
}

function getDefaultPrice(category) {
  const value = normalizeText(category).toLowerCase();
  if (value.includes('elétrica') || value.includes('eletrica')) return 110.0;
  if (value.includes('encanador') || value.includes('hidráulica') || value.includes('hidraulica')) return 160.0;
  if (value.includes('instalação') || value.includes('instalacao')) return 160.0;
  if (value.includes('faxina')) return 190.0;
  if (value.includes('desentupimento')) return 180.0;
  if (value.includes('limpeza de estofados') || value.includes('estofado')) return 215.0;
  if (value.includes('chaveiro')) return 180.0;
  if (value.includes('automotivo')) return 115.0;
  if (value.includes('montagem')) return 150.0;
  if (value.includes('ar condicionado')) return 650.0;
  if (value.includes('caça-vazamento') || value.includes('caca-vazamento')) return 550.0;
  if (value.includes('caixa')) return 150.0;
  if (value.includes('eletrodoméstico') || value.includes('eletrodomestico')) return 160.0;
  return deriveClientPriceFromProvider(getDefaultProviderValue(category)) || 100.0;
}

function getDefaultProviderValue(category) {
  const value = normalizeText(category).toLowerCase();
  if (value.includes('elétrica') || value.includes('eletrica')) return 55.0;
  if (value.includes('encanador') || value.includes('hidráulica') || value.includes('hidraulica')) return 80.0;
  if (value.includes('instalação') || value.includes('instalacao')) return 80.0;
  if (value.includes('faxina')) return 133.0;
  if (value.includes('desentupimento')) return 90.0;
  if (value.includes('limpeza de estofados') || value.includes('estofado')) return 129.0;
  if (value.includes('chaveiro')) return 108.0;
  if (value.includes('automotivo')) return 57.50;
  if (value.includes('montagem')) return 75.0;
  if (value.includes('ar condicionado')) return 364.0;
  if (value.includes('caça-vazamento') || value.includes('caca-vazamento')) return 385.0;
  if (value.includes('caixa')) return 75.0;
  if (value.includes('eletrodoméstico') || value.includes('eletrodomestico')) return 80.0;
  return 50.0;
}

function roundMoney(value) {
  return Math.round(Number(value) * 100) / 100;
}

// Lê o preço/repasse do catálogo dinâmico (catalog_services) gerido pelo painel admin.
// Fonte de verdade quando disponível; em qualquer falha (sem credencial, erro, miss,
// serviço "A consultar"/inativo) retorna null para cair na tabela hardcoded — NUNCA lança.
async function lookupFirestorePricing(category, serviceType) {
  const cacheKey = `${normalizeText(category).toLowerCase()}||${normalizeText(serviceType).toLowerCase()}`;
  const cached = firestorePricingCache.get(cacheKey);
  if (cached && cached.expires > Date.now()) {
    return cached.value;
  }

  let value = null;
  try {
    const admin = initializeFirebase();
    if (!admin.apps || admin.apps.length === 0) {
      // Sem credenciais Firebase → usa fallback hardcoded.
      firestorePricingCache.set(cacheKey, { value: null, expires: Date.now() + FIRESTORE_PRICING_TTL_MS });
      return null;
    }

    const db = admin.firestore();
    const requested = normalizeText(serviceType).toLowerCase();

    // Tenta o nicho exato e, em seguida, o nicho normalizado (ex.: "Hidráulica" → "Encanador").
    const niches = [normalizeText(category)];
    const normalized = normalizeCategory(category);
    if (normalized && normalized !== niches[0]) niches.push(normalized);

    let match = null;
    for (const niche of niches) {
      // eslint-disable-next-line no-await-in-loop
      const snap = await db.collection('catalog_services').where('niche', '==', niche).get();
      match = snap.docs.find((doc) => {
        const d = doc.data() || {};
        const name = String(d.name || d.title || d.label || '').toLowerCase();
        return name === requested;
      });
      if (match) break;
    }

    if (match) {
      const d = match.data() || {};
      const active = d.active !== false && d.isActive !== false && d.enabled !== false;
      const price = Number(d.estimatedPrice) || 0;
      const commission = Number(d.providerCommission) || 0;
      // "A consultar"/inativo/sem preço → null para cair no fallback/default existente.
      if (active && d.isConsult !== true && price > 0) {
        value = {
          category: normalizeCategory(category),
          serviceType: normalizeText(serviceType),
          estimatedPrice: roundMoney(price),
          providerCommission: roundMoney(commission),
          source: 'firestore_catalog'
        };
      }
    }
  } catch (error) {
    logger.warn('Falha ao consultar catalog_services no Firestore — usando fallback', {
      message: error && error.message
    });
    value = null;
  }

  firestorePricingCache.set(cacheKey, { value, expires: Date.now() + FIRESTORE_PRICING_TTL_MS });
  return value;
}

async function calculateServicePricing({ category, serviceType, distanceKm }) {
  const cleanCategory = normalizeText(category);
  const cleanServiceType = normalizeText(serviceType);
  if (!cleanCategory) {
    throw new HttpError(422, 'Categoria do serviço é obrigatória', { code: 'INVALID_SERVICE_CATEGORY' });
  }
  if (!cleanServiceType) {
    throw new HttpError(422, 'Tipo de serviço é obrigatório', { code: 'INVALID_SERVICE_TYPE' });
  }

  // 0) Guincho: preço por distância (saída + R$/km da origem ao destino).
  //    O app envia distanceKm calculado pela rota; ignora o catálogo fixo.
  if (isTowingCategory(cleanCategory)) {
    return calculateTowingPricing({
      category: cleanCategory,
      serviceType: cleanServiceType,
      distanceKm
    });
  }

  // 1) Catálogo dinâmico do painel (fonte de verdade quando disponível).
  const fromFirestore = await lookupFirestorePricing(cleanCategory, cleanServiceType);
  if (fromFirestore) {
    return fromFirestore;
  }

  // 2) Fallback: tabela hardcoded (comportamento legado, idêntico ao anterior).
  const pair = getPair(cleanCategory, cleanServiceType);
  let estimatedPrice;
  let providerCommission;
  let source = 'default';

  if (pair && Number(pair[0]) > 0) {
    estimatedPrice = Number(pair[0]);
    providerCommission = Number(pair[1]) > 0 ? Number(pair[1]) : getDefaultProviderValue(cleanCategory);
    source = 'specific';
  } else if (pair && Number(pair[1]) > 0) {
    providerCommission = Number(pair[1]);
    estimatedPrice = deriveClientPriceFromProvider(providerCommission) || getDefaultPrice(cleanCategory);
    source = 'derived_from_provider_value';
  } else {
    estimatedPrice = getDefaultPrice(cleanCategory);
    providerCommission = getDefaultProviderValue(cleanCategory);
  }

  return {
    category: normalizeCategory(cleanCategory),
    serviceType: cleanServiceType,
    estimatedPrice: roundMoney(estimatedPrice),
    providerCommission: roundMoney(providerCommission),
    source
  };
}

module.exports = {
  calculateServicePricing,
  getDefaultPrice,
  getDefaultProviderValue,
  clearPricingCache
};
