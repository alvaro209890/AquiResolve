const { test } = require('node:test');
const assert = require('node:assert');
const {
  buildVisionSystemPrompt,
  parseVisionResult,
  toImageDataUrl,
} = require('../src/services/ai-vision.service');

test('buildVisionSystemPrompt: ancora nos nichos e pede a tag [NICHE:]', () => {
  const p = buildVisionSystemPrompt(['Elétrica', 'Encanador']);
  assert.match(p, /- Elétrica/);
  assert.match(p, /- Encanador/);
  assert.match(p, /\[NICHE:Nome Exato\]/);
  assert.match(p, /NUNCA invente nichos/);
});

test('parseVisionResult: extrai texto + nicho + slug', () => {
  const r = parseVisionResult('Vejo um fio exposto na parede.\n[NICHE:Elétrica]');
  assert.strictEqual(r.text, 'Vejo um fio exposto na parede.');
  assert.strictEqual(r.niche, 'Elétrica');
  assert.strictEqual(r.nicheSlug, 'eletrica');
});

test('parseVisionResult: sem tag => niche null', () => {
  const r = parseVisionResult('A foto está escura, pode mandar outra?');
  assert.strictEqual(r.niche, null);
  assert.strictEqual(r.nicheSlug, null);
  assert.match(r.text, /escura/);
});

test('toImageDataUrl: aceita base64 puro e data URL', () => {
  assert.strictEqual(toImageDataUrl('AAAA', 'image/png'), 'data:image/png;base64,AAAA');
  assert.strictEqual(toImageDataUrl('data:image/jpeg;base64,XYZ'), 'data:image/jpeg;base64,XYZ');
  assert.strictEqual(toImageDataUrl('AAAA'), 'data:image/jpeg;base64,AAAA'); // default mime
  assert.strictEqual(toImageDataUrl(''), null);
});
