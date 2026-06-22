// Semeia banners de teste na coleção `home_banners` (carrossel da Home).
// Idempotente: ids determinísticos + merge. Rode de dentro de `dashboard_admin/` com Node >= 20:
//   node scripts/seed-banners.mjs
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
    id: 'seed-cashback',
    title: 'Ganhe cashback em todo serviço',
    subtitle: 'Até 8% de volta no AquiCash',
    imageUrl: 'https://picsum.photos/seed/aquiresolve-cashback/1200/500',
    actionType: 'cashback',
    actionValue: '',
    backgroundColor: '#0E2A47',
    active: true,
    displayOrder: 0,
  },
  {
    id: 'seed-eletrica',
    title: 'Problema elétrico? Resolvemos',
    subtitle: 'Profissionais avaliados perto de você',
    imageUrl: 'https://picsum.photos/seed/aquiresolve-eletrica/1200/500',
    actionType: 'niche',
    actionValue: 'Elétrica',
    backgroundColor: '#FF7A00',
    active: true,
    displayOrder: 1,
  },
  {
    id: 'seed-limpeza',
    title: 'Limpeza com tudo em ordem',
    subtitle: 'Agende em poucos toques',
    imageUrl: 'https://picsum.photos/seed/aquiresolve-limpeza/1200/500',
    actionType: 'service',
    actionValue: 'Limpeza',
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
    await db.collection('home_banners').doc(id).set(
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
  console.log(`\nConcluído: ${created} banners semeados em home_banners.`)
  process.exit(0)
}

main().catch((e) => {
  console.error('Falha ao semear banners:', e.message)
  process.exit(1)
})
