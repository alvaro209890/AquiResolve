const crypto = require('crypto');

/**
 * Validação de autenticidade do webhook Pagar.me (lógica pura, testável).
 *
 * A Pagar.me v5 permite configurar a autenticação do webhook de formas
 * diferentes conforme a conta (Basic auth no cadastro do endpoint, header
 * customizado, ou assinatura HMAC do corpo). Para não depender de qual modo
 * o dashboard oferecer, aceitamos QUALQUER um dos mecanismos abaixo, sempre
 * com comparação constant-time:
 *
 *  1. HMAC do raw body (sha256 ou sha1) em `x-hub-signature-256`,
 *     `x-hub-signature`, `x-pagarme-signature` ou `x-signature`,
 *     com ou sem prefixo `sha256=`/`sha1=`.
 *  2. `Authorization: Basic ...` — bate se a senha, o usuário ou o par
 *     `user:pass` inteiro for igual ao secret.
 *  3. `Authorization: Bearer <secret>`.
 *  4. Secret estático em `x-pagarme-webhook-secret`, `x-payment-webhook-secret`,
 *     `x-webhook-secret` ou query `?secret=` (compatível com o comportamento antigo).
 *
 * Sem secret configurado no ambiente, o webhook é aceito (comportamento
 * legado, para não quebrar antes da configuração) — o chamador loga warning.
 */

const STATIC_SECRET_HEADERS = [
  'x-pagarme-webhook-secret',
  'x-payment-webhook-secret',
  'x-webhook-secret'
];

const SIGNATURE_HEADERS = [
  'x-hub-signature-256',
  'x-hub-signature',
  'x-pagarme-signature',
  'x-signature'
];

function normalizeHeaderValue(value) {
  if (Array.isArray(value)) {
    return normalizeHeaderValue(value[0]);
  }
  return typeof value === 'string' ? value.trim() : '';
}

function timingSafeEqualStrings(a, b) {
  const bufferA = Buffer.from(String(a), 'utf8');
  const bufferB = Buffer.from(String(b), 'utf8');
  if (bufferA.length !== bufferB.length) {
    return false;
  }
  return crypto.timingSafeEqual(bufferA, bufferB);
}

function stripSignaturePrefix(value) {
  const match = /^(sha256|sha1)=(.+)$/i.exec(value);
  if (match) {
    return { algorithm: match[1].toLowerCase(), signature: match[2].trim() };
  }
  return { algorithm: null, signature: value };
}

function computeHmacHex(algorithm, secret, rawBody) {
  return crypto.createHmac(algorithm, secret).update(rawBody).digest('hex');
}

function matchesHmacSignature({ providedSignature, secret, rawBody }) {
  const { algorithm, signature } = stripSignaturePrefix(providedSignature);
  const normalized = signature.toLowerCase();
  const algorithms = algorithm ? [algorithm] : ['sha256', 'sha1'];

  for (const alg of algorithms) {
    const expected = computeHmacHex(alg, secret, rawBody);
    if (timingSafeEqualStrings(normalized, expected)) {
      return alg === 'sha256' ? 'hmac-sha256' : 'hmac-sha1';
    }
  }
  return null;
}

function matchesAuthorizationHeader({ authorization, secret }) {
  const value = normalizeHeaderValue(authorization);
  if (!value) return null;

  const bearerMatch = /^Bearer\s+(.+)$/i.exec(value);
  if (bearerMatch && timingSafeEqualStrings(bearerMatch[1].trim(), secret)) {
    return 'bearer';
  }

  const basicMatch = /^Basic\s+(.+)$/i.exec(value);
  if (basicMatch) {
    let decoded = '';
    try {
      decoded = Buffer.from(basicMatch[1].trim(), 'base64').toString('utf8');
    } catch (_) {
      return null;
    }
    if (timingSafeEqualStrings(decoded, secret)) {
      return 'basic-auth';
    }
    const separatorIndex = decoded.indexOf(':');
    if (separatorIndex >= 0) {
      const user = decoded.slice(0, separatorIndex);
      const password = decoded.slice(separatorIndex + 1);
      if (timingSafeEqualStrings(password, secret) || timingSafeEqualStrings(user, secret)) {
        return 'basic-auth';
      }
    }
  }

  return null;
}

/**
 * @param {object} params
 * @param {object} params.headers - req.headers (chaves minúsculas, como o Express entrega)
 * @param {object} [params.query] - req.query
 * @param {Buffer|string} [params.rawBody] - corpo cru da requisição (para HMAC)
 * @param {string} params.secret - PAGARME_WEBHOOK_SECRET configurado
 * @returns {{ ok: boolean, method: string|null }}
 */
function verifyWebhookRequest({ headers = {}, query = {}, rawBody, secret }) {
  const expectedSecret = typeof secret === 'string' ? secret.trim() : '';
  if (!expectedSecret) {
    return { ok: true, method: 'none-configured' };
  }

  // 1. Assinatura HMAC do raw body
  if (rawBody !== undefined && rawBody !== null) {
    for (const header of SIGNATURE_HEADERS) {
      const provided = normalizeHeaderValue(headers[header]);
      if (!provided) continue;
      const method = matchesHmacSignature({
        providedSignature: provided,
        secret: expectedSecret,
        rawBody
      });
      if (method) {
        return { ok: true, method };
      }
    }
  }

  // 2/3. Authorization: Basic / Bearer
  const authMethod = matchesAuthorizationHeader({
    authorization: headers.authorization,
    secret: expectedSecret
  });
  if (authMethod) {
    return { ok: true, method: authMethod };
  }

  // 4. Secret estático (header ou query) — compatibilidade com o formato antigo
  const staticCandidates = STATIC_SECRET_HEADERS
    .map((header) => normalizeHeaderValue(headers[header]))
    .concat(normalizeHeaderValue(query && query.secret));

  for (const candidate of staticCandidates) {
    if (candidate && timingSafeEqualStrings(candidate, expectedSecret)) {
      return { ok: true, method: 'static-secret' };
    }
  }

  return { ok: false, method: null };
}

module.exports = {
  verifyWebhookRequest,
  timingSafeEqualStrings
};
