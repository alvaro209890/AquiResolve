// Semeia parceiros de teste na coleção `partners` (seção "Parceiros AquiResolve" da Home).
// Idempotente: ids determinísticos + merge. Logos via picsum (placeholders) — substitua no painel.
//   Rode de dentro de `dashboard_admin/` com Node >= 20:  node scripts/seed-partners.mjs
import { readFileSync } from 'node:fs'
import admin from 'firebase-admin'

function esc(v) {
  let f = '', i = false, e = false
  for (const c of v) {
    if (e) { f += c; e = false }
    else if (c === '\\' && i) { f += c; e = true }
    else if (c === '"') { i = !i; f += c }
    else if (c === '\n' && i) { f += '\\n' }
    else { f += c }
  }
  return f
}

function loadServiceAccount() {
  const raw = readFileSync('.env.local', 'utf8')
  const line = raw.split(/\r?\n/).find((l) => l.startsWith('FIREBASE_SERVICE_ACCOUNT='))
  if (!line) throw new Error('FIREBASE_SERVICE_ACCOUNT ausente em .env.local')
  const v = line.slice('FIREBASE_SERVICE_ACCOUNT='.length).trim()
  const attempts = [
    () => JSON.parse(v),
    () => { const s = JSON.parse(v); return typeof s === 'string' ? JSON.parse(esc(s)) : s },
    () => { let c = v; if (v.includes('\\"')) c = v.replace(/\\"/g, '"'); return JSON.parse(esc(c)) },
  ]
  for (const a of attempts) {
    try {
      const p = a()
      if (p && p.private_key) { p.private_key = p.private_key.replace(/\\n/g, '\n'); return p }
    } catch { /* tenta o próximo */ }
  }
  throw new Error('Não foi possível decodificar FIREBASE_SERVICE_ACCOUNT')
}

async function main() {
  admin.initializeApp({ credential: admin.credential.cert(loadServiceAccount()) })
  const db = admin.firestore()

  const partners = [
    {
      id: 'seed-leroy',
      name: 'Leroy Merlin',
      logoUrl: 'https://picsum.photos/seed/leroy-logo/300/300',
      bannerUrl: 'https://picsum.photos/seed/leroy-banner/1200/500',
      description: 'Materiais de construção e reforma com desconto exclusivo para clientes AquiResolve.',
      benefitType: 'discount',
      benefitLabel: '💰 10% OFF na primeira compra',
      couponCode: '',
      url: 'https://www.leroymerlin.com.br',
      displayOrder: 0,
    },
    {
      id: 'seed-telhanorte',
      name: 'Telhanorte',
      logoUrl: 'https://picsum.photos/seed/telha-logo/300/300',
      bannerUrl: 'https://picsum.photos/seed/telha-banner/1200/500',
      description: 'Acabamentos e materiais para sua obra. Use o cupom e economize.',
      benefitType: 'coupon',
      benefitLabel: '🎟️ Cupom AQUI15',
      couponCode: 'AQUI15',
      url: 'https://www.telhanorte.com.br',
      displayOrder: 1,
    },
    {
      id: 'seed-casaeconstrucao',
      name: 'Casa & Construção',
      logoUrl: 'https://picsum.photos/seed/casa-logo/300/300',
      bannerUrl: '',
      description: 'Ferramentas e elétrica com cashback para quem contrata pelo AquiResolve.',
      benefitType: 'cashback',
      benefitLabel: '💰 5% de cashback',
      couponCode: '',
      url: 'https://example.com/casa-construcao',
      displayOrder: 2,
    },
  ]

  for (const p of partners) {
    const { id, ...rest } = p
    const active = true
    await db.collection('partners').doc(id).set({
      ...rest,
      active, isActive: active, enabled: active,
      order: rest.displayOrder, sortOrder: rest.displayOrder,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true })
    console.log(`✔ ${p.name} · ${p.benefitType} · "${p.benefitLabel}"`)
  }

  console.log(`\nSemeados ${partners.length} parceiros em partners.`)
  process.exit(0)
}

main().catch((e) => { console.error('ERRO:', e.message); process.exit(1) })
