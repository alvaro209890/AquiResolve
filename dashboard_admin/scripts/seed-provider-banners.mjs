// Semeia banners de teste na coleção `provider_banners` (carrossel da Home do PRESTADOR).
// Idempotente: ids determinísticos + merge. Rode de dentro de `dashboard_admin/` com Node >= 20:
//   node scripts/seed-provider-banners.mjs
// Obs.: o prestador NÃO tem cashback — actionType válido aqui é: niche | service | url | none.
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

const BANNERS = [
  {
    id: 'seed-prov-ganhos',
    title: 'Aumente seus ganhos',
    subtitle: 'Aceite mais pedidos e suba no ranking',
    imageUrl: 'https://picsum.photos/seed/aquiresolve-prov-ganhos/1200/500',
    actionType: 'none',
    actionValue: '',
    backgroundColor: '#0E2A47',
    active: true,
    displayOrder: 0,
  },
  {
    id: 'seed-prov-especialidades',
    title: 'Adicione novas especialidades',
    subtitle: 'Receba pedidos de mais nichos perto de você',
    imageUrl: 'https://picsum.photos/seed/aquiresolve-prov-nichos/1200/500',
    actionType: 'none',
    actionValue: '',
    backgroundColor: '#FF7A00',
    active: true,
    displayOrder: 1,
  },
  {
    id: 'seed-prov-disponivel',
    title: 'Fique disponível e receba mais',
    subtitle: 'Ative sua disponibilidade para não perder pedidos',
    imageUrl: 'https://picsum.photos/seed/aquiresolve-prov-online/1200/500',
    actionType: 'none',
    actionValue: '',
    backgroundColor: '#10B981',
    active: true,
    displayOrder: 2,
  },
]

async function main() {
  const sa = loadServiceAccount()
  if (admin.apps.length === 0) {
    admin.initializeApp({ credential: admin.credential.cert(sa) })
  }
  const db = admin.firestore()
  let created = 0
  for (const b of BANNERS) {
    const { id, ...rest } = b
    await db.collection('provider_banners').doc(id).set(
      {
        ...rest,
        isActive: rest.active,
        enabled: rest.active,
        order: rest.displayOrder,
        sortOrder: rest.displayOrder,
        updatedAt: admin.firestore.FieldValue.serverTimestamp(),
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    )
    created++
    console.log(`✓ ${id} (${rest.actionType})`)
  }
  console.log(`\nConcluído: ${created} banners semeados em provider_banners.`)
  process.exit(0)
}

main().catch((e) => {
  console.error('Falha ao semear banners de prestador:', e.message)
  process.exit(1)
})
