/**
 * Semeia o GUINCHO no AquiResolve:
 *  - nicho "Guincho" em `service_categories` + `service_types` (para aparecer na busca/grade do app);
 *  - documento de configuração `app_config/guincho` com a precificação padrão
 *    (taxa de saída, R$/km, % do motorista). Idempotente: faz merge, não apaga
 *    valores já ajustados no painel.
 *
 * Como rodar (a partir de dashboard_admin/, que tem firebase-admin e .env.local):
 *   node scripts/seed-guincho.mjs
 */

import admin from 'firebase-admin'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))

function escapeNewlinesInsideJsonStrings(value) {
  let fixed = ''
  let inString = false
  let escaped = false
  for (const char of value) {
    if (escaped) {
      fixed += char
      escaped = false
    } else if (char === '\\' && inString) {
      fixed += char
      escaped = true
    } else if (char === '"') {
      inString = !inString
      fixed += char
    } else if (char === '\n' && inString) {
      fixed += '\\n'
    } else {
      fixed += char
    }
  }
  return fixed
}

function loadServiceAccount() {
  let value = process.env.FIREBASE_SERVICE_ACCOUNT
  if (!value) {
    const envPath = resolve(__dirname, '..', '.env.local')
    const raw = readFileSync(envPath, 'utf8')
    const line = raw.split(/\r?\n/).find((l) => l.startsWith('FIREBASE_SERVICE_ACCOUNT='))
    if (!line) throw new Error('FIREBASE_SERVICE_ACCOUNT não encontrado em .env.local')
    value = line.slice('FIREBASE_SERVICE_ACCOUNT='.length).trim()
  }
  const trimmed = value.trim()
  const attempts = [
    () => JSON.parse(trimmed),
    () => {
      const s = JSON.parse(trimmed)
      if (typeof s !== 'string') return s
      return JSON.parse(escapeNewlinesInsideJsonStrings(s))
    },
    () => {
      const cands = [trimmed]
      if (trimmed.includes('\\"')) cands.push(trimmed.replace(/\\"/g, '"'))
      for (const c of cands) {
        try {
          return JSON.parse(escapeNewlinesInsideJsonStrings(c))
        } catch {
          /* próximo */
        }
      }
      throw new Error('fallback')
    },
  ]
  for (const attempt of attempts) {
    try {
      const parsed = attempt()
      if (parsed && typeof parsed === 'object' && parsed.private_key) {
        parsed.private_key = parsed.private_key.replace(/\\n/g, '\n')
        return parsed
      }
    } catch {
      /* tenta a próxima */
    }
  }
  throw new Error('Não foi possível parsear FIREBASE_SERVICE_ACCOUNT')
}

function normalizeKey(value) {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
    .replace(/\s+/g, ' ')
}

async function main() {
  const sa = loadServiceAccount()
  if (!admin.apps.length) {
    admin.initializeApp({ credential: admin.credential.cert(sa) })
  }
  const db = admin.firestore()

  // 1) Config de precificação do guincho.
  const config = {
    enabled: true,
    baseFee: 180.0,
    pricePerKm: 3.9,
    providerPercent: 70,
    minKm: 0,
    updatedAt: admin.firestore.Timestamp.now(),
  }
  await db.collection('app_config').doc('guincho').set(config, { merge: true })
  console.log('+ app_config/guincho:', JSON.stringify(config))

  // 2) Nicho "Guincho" para aparecer na busca/grade do app.
  const niche = {
    name: 'Guincho',
    title: 'Guincho',
    label: 'Guincho',
    slug: 'guincho',
    icon: 'truck',
    active: true,
    isActive: true,
    enabled: true,
    aliases: ['guincho', 'reboque', 'transporte de veiculo', 'transporte de carro', 'pane'],
    keywords: ['guincho', 'reboque', 'transporte de veiculo', 'transporte de carro', 'pane'],
    pricingMode: 'distance', // sinaliza precificação por km (origem→destino)
    updatedAt: admin.firestore.Timestamp.now(),
  }

  // Evita duplicar se já existir um doc com o mesmo nome em outro id.
  const existing = await db.collection('service_categories').get()
  let targetId = 'guincho'
  for (const doc of existing.docs) {
    const d = doc.data() || {}
    const name = String(d.name ?? d.title ?? d.label ?? '').trim()
    if (name && normalizeKey(name) === normalizeKey(niche.name)) {
      targetId = doc.id
      break
    }
  }
  await db.collection('service_categories').doc(targetId).set(niche, { merge: true })
  await db.collection('service_types').doc(targetId).set(
    { ...niche, createdAt: admin.firestore.Timestamp.now() },
    { merge: true }
  )
  console.log(`+ nicho Guincho (${targetId})`)

  console.log('\nOK — guincho semeado. Ajuste saída/R$ por km/% no painel quando quiser.')
  process.exit(0)
}

main().catch((err) => {
  console.error('Falha no seed do guincho:', err)
  process.exit(1)
})
