const { test } = require('node:test');
const assert = require('node:assert');
const crypto = require('crypto');

const { verifyWebhookRequest } = require('../src/utils/webhook-auth');
const { extractWebhookEventId } = require('../src/services/webhook-event.service');

const SECRET = 'wh_sk_test_123';
const BODY = Buffer.from(JSON.stringify({ id: 'hook_1', type: 'order.paid', data: { id: 'or_abc' } }));

function sign(algorithm, body = BODY, secret = SECRET) {
  return crypto.createHmac(algorithm, secret).update(body).digest('hex');
}

test('sem secret configurado: aceita e sinaliza none-configured', () => {
  const r = verifyWebhookRequest({ headers: {}, query: {}, rawBody: BODY, secret: '' });
  assert.strictEqual(r.ok, true);
  assert.strictEqual(r.method, 'none-configured');
});

test('HMAC sha256 com prefixo sha256= valida', () => {
  const r = verifyWebhookRequest({
    headers: { 'x-hub-signature-256': `sha256=${sign('sha256')}` },
    rawBody: BODY,
    secret: SECRET
  });
  assert.deepStrictEqual(r, { ok: true, method: 'hmac-sha256' });
});

test('HMAC sem prefixo valida (tenta sha256 e sha1)', () => {
  const sha256 = verifyWebhookRequest({
    headers: { 'x-pagarme-signature': sign('sha256') },
    rawBody: BODY,
    secret: SECRET
  });
  assert.strictEqual(sha256.method, 'hmac-sha256');

  const sha1 = verifyWebhookRequest({
    headers: { 'x-hub-signature': `sha1=${sign('sha1')}` },
    rawBody: BODY,
    secret: SECRET
  });
  assert.strictEqual(sha1.method, 'hmac-sha1');
});

test('HMAC de corpo adulterado é rejeitado', () => {
  const r = verifyWebhookRequest({
    headers: { 'x-hub-signature-256': `sha256=${sign('sha256')}` },
    rawBody: Buffer.from('{"id":"hook_1","amount":999999}'),
    secret: SECRET
  });
  assert.strictEqual(r.ok, false);
});

test('HMAC com secret errado é rejeitado', () => {
  const r = verifyWebhookRequest({
    headers: { 'x-hub-signature-256': `sha256=${sign('sha256', BODY, 'outro-secret')}` },
    rawBody: BODY,
    secret: SECRET
  });
  assert.strictEqual(r.ok, false);
});

test('Basic auth: senha igual ao secret valida', () => {
  const token = Buffer.from(`pagarme:${SECRET}`).toString('base64');
  const r = verifyWebhookRequest({
    headers: { authorization: `Basic ${token}` },
    rawBody: BODY,
    secret: SECRET
  });
  assert.deepStrictEqual(r, { ok: true, method: 'basic-auth' });
});

test('Basic auth: user:pass inteiro igual ao secret valida', () => {
  const paired = 'usuario:senha';
  const token = Buffer.from(paired).toString('base64');
  const r = verifyWebhookRequest({
    headers: { authorization: `Basic ${token}` },
    rawBody: BODY,
    secret: paired
  });
  assert.deepStrictEqual(r, { ok: true, method: 'basic-auth' });
});

test('Bearer token igual ao secret valida', () => {
  const r = verifyWebhookRequest({
    headers: { authorization: `Bearer ${SECRET}` },
    rawBody: BODY,
    secret: SECRET
  });
  assert.deepStrictEqual(r, { ok: true, method: 'bearer' });
});

test('secret estático em header (formato antigo) valida', () => {
  const r = verifyWebhookRequest({
    headers: { 'x-pagarme-webhook-secret': SECRET },
    rawBody: BODY,
    secret: SECRET
  });
  assert.deepStrictEqual(r, { ok: true, method: 'static-secret' });
});

test('secret estático via query ?secret= valida', () => {
  const r = verifyWebhookRequest({
    headers: {},
    query: { secret: SECRET },
    rawBody: BODY,
    secret: SECRET
  });
  assert.deepStrictEqual(r, { ok: true, method: 'static-secret' });
});

test('sem nenhuma credencial: rejeita quando há secret configurado', () => {
  const r = verifyWebhookRequest({ headers: {}, query: {}, rawBody: BODY, secret: SECRET });
  assert.deepStrictEqual(r, { ok: false, method: null });
});

test('secret com tamanho diferente não lança (comparação segura)', () => {
  const r = verifyWebhookRequest({
    headers: { 'x-webhook-secret': 'curto' },
    rawBody: BODY,
    secret: SECRET
  });
  assert.strictEqual(r.ok, false);
});

test('extractWebhookEventId: usa payload.id quando difere do id do pedido', () => {
  assert.strictEqual(extractWebhookEventId({ id: 'hook_1', data: { id: 'or_abc' } }, 'or_abc'), 'hook_1');
});

test('extractWebhookEventId: vazio quando payload.id é o próprio id do pedido', () => {
  assert.strictEqual(extractWebhookEventId({ id: 'or_abc' }, 'or_abc'), '');
});

test('extractWebhookEventId: vazio sem id', () => {
  assert.strictEqual(extractWebhookEventId({}, 'or_abc'), '');
  assert.strictEqual(extractWebhookEventId(null, 'or_abc'), '');
});
