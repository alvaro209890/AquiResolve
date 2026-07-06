import * as admin from 'firebase-admin'

type ParsedAccount = {
  projectId: string
  clientEmail: string
  privateKey: string
}

function parseServiceAccountJson(raw: string): ParsedAccount {
  let parsed: Record<string, unknown>
  try {
    parsed = JSON.parse(raw.trim()) as Record<string, unknown>
  } catch {
    throw new Error('FIREBASE_SERVICE_ACCOUNT não é um JSON válido.')
  }

  const projectId = (parsed.project_id ?? parsed.projectId) as string | undefined
  const clientEmail = (parsed.client_email ?? parsed.clientEmail) as string | undefined
  const privateKeyRaw = (parsed.private_key ?? parsed.privateKey) as string | undefined

  if (!projectId || !clientEmail || !privateKeyRaw) {
    throw new Error(
      'FIREBASE_SERVICE_ACCOUNT precisa de project_id, client_email e private_key (JSON do Firebase).',
    )
  }

  // .env / Vercel / Windows: muitas vezes a chave vem com "\n" literal (dois caracteres) em vez de newline real
  let privateKey = privateKeyRaw.trim()
  privateKey = privateKey.replace(/\\n/g, '\n')
  // Caso tenha sido escapado em dupla camada
  privateKey = privateKey.replace(/\r\n/g, '\n')

  if (!privateKey.includes('BEGIN') || !privateKey.includes('PRIVATE KEY')) {
    throw new Error(
      'private_key não parece um PEM válido. Confira se o JSON do Service Account está completo e em uma linha, com \\n entre as linhas da chave.',
    )
  }

  return { projectId, clientEmail, privateKey }
}

function getServiceAccount(): admin.ServiceAccount {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT
  if (!raw) {
    throw new Error('Variável FIREBASE_SERVICE_ACCOUNT não configurada.')
  }
  const { projectId, clientEmail, privateKey } = parseServiceAccountJson(raw)
  return { projectId, clientEmail, privateKey }
}

export const adminApp: admin.app.App = (() => {
  if (admin.apps.length > 0) return admin.apps[0]!
  return admin.initializeApp({
    credential: admin.credential.cert(getServiceAccount()),
    storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  })
})()

export function getAdminAuth(): admin.auth.Auth {
  return admin.auth(adminApp)
}

export function getAdminFirestore(): admin.firestore.Firestore {
  return admin.firestore(adminApp)
}

export function getAdminStorage(): admin.storage.Storage {
  return admin.storage(adminApp)
}
