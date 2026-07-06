import * as admin from 'firebase-admin'
import { getAdminAuth, getAdminFirestore, getAdminStorage } from '@/lib/firebase-admin'

interface PhaseResult {
  success: boolean
  error?: string
  count?: number
}

export interface DeletionResult {
  phases: {
    storage: PhaseResult
    firestore: PhaseResult
    lgpdLog: PhaseResult
    authDelete: PhaseResult
  }
  deletedResources: Record<string, number>
}

interface UserFirestoreData {
  role?: string
  email?: string
  nome?: string
  displayName?: string
}

export class AccountDeletionService {
  private db: admin.firestore.Firestore
  private auth: admin.auth.Auth
  private storage: admin.storage.Storage

  constructor() {
    this.db = getAdminFirestore()
    this.auth = getAdminAuth()
    this.storage = getAdminStorage()
  }

  async deleteAccount(userId: string, userEmail: string): Promise<DeletionResult> {
    const result: DeletionResult = {
      phases: {
        storage: { success: false },
        firestore: { success: false },
        lgpdLog: { success: false },
        authDelete: { success: false },
      },
      deletedResources: {},
    }

    // Phase 2 — Delete Storage files
    try {
      const count = await this.deleteStorageFiles(userId)
      result.phases.storage = { success: true, count }
      result.deletedResources.storageFiles = count
      console.log(`[Phase 2] Storage: deleted ${count} files`)
    } catch (e: unknown) {
      result.phases.storage = { success: false, error: toMessage(e) }
      console.error('[Phase 2] Storage deletion failed:', e)
    }

    // Phase 3 — Delete/Anonymize Firestore data
    try {
      const counts = await this.deleteFirestoreData(userId, userEmail)
      result.phases.firestore = { success: true }
      Object.assign(result.deletedResources, counts)
      console.log('[Phase 3] Firestore done:', counts)
    } catch (e: unknown) {
      result.phases.firestore = { success: false, error: toMessage(e) }
      console.error('[Phase 3] Firestore deletion failed:', e)
    }

    // Phase 4 — LGPD log (before deleting auth)
    try {
      await this.logLgpdDeletion(userId, userEmail, result.deletedResources)
      result.phases.lgpdLog = { success: true }
      console.log('[Phase 4] LGPD log written')
    } catch (e: unknown) {
      result.phases.lgpdLog = { success: false, error: toMessage(e) }
      console.error('[Phase 4] LGPD log failed:', e)
    }

    // Phase 5 — Delete Firebase Auth user (LAST)
    try {
      await this.auth.deleteUser(userId)
      result.phases.authDelete = { success: true }
      console.log('[Phase 5] Auth user deleted')
    } catch (e: unknown) {
      result.phases.authDelete = { success: false, error: toMessage(e) }
      console.error('[Phase 5] Auth deletion failed:', e)
    }

    return result
  }

  // ─── Phase 2: Storage ────────────────────────────────────────────────────────

  private async deleteStorageFiles(userId: string): Promise<number> {
    const bucket = this.storage.bucket()
    const prefixes = [
      `Documentos/${userId}/`,
      `documentos/${userId}/`,
      `Providers/${userId}/`,
      `providers/${userId}/`,
      `documentos_usuario/${userId}/`,
    ]

    let total = 0

    for (const prefix of prefixes) {
      try {
        const [files] = await bucket.getFiles({ prefix })
        for (const file of files) {
          try {
            await file.delete()
            total++
          } catch (e: unknown) {
            const code = (e as { code?: number })?.code
            if (code !== 404) console.error(`[Storage] Failed to delete ${file.name}:`, e)
          }
        }
      } catch (e: unknown) {
        const code = (e as { code?: number })?.code
        if (code !== 404) console.error(`[Storage] Error listing prefix ${prefix}:`, e)
      }
    }

    return total
  }

  // ─── Phase 3: Firestore ──────────────────────────────────────────────────────

  private async deleteFirestoreData(
    userId: string,
    userEmail: string,
  ): Promise<Record<string, number>> {
    const counts: Record<string, number> = {}

    // --- users (direct doc delete)
    await this.safeRun('users', async () => {
      await this.db.collection('users').doc(userId).delete()
      counts.users = 1
    })

    // --- providers (direct doc delete)
    await this.safeRun('providers', async () => {
      await this.db.collection('providers').doc(userId).delete()
      counts.providers = 1
    })

    // --- accounts (where userId)
    await this.safeRun('accounts', async () => {
      counts.accounts = await this.deleteDocs(
        this.db.collection('accounts').where('userId', '==', userId),
      )
    })

    // --- simple deletes by providerId
    for (const col of [
      'provider_verifications',
      'provider_locations',
      'provider_logins',
      'verification_history',
    ] as const) {
      await this.safeRun(col, async () => {
        counts[col] = await this.deleteDocs(
          this.db.collection(col).where('providerId', '==', userId),
        )
      })
    }

    // --- simple deletes by userId
    for (const col of [
      'saved_addresses',
      'activities',
      'analytics_events',
      'support_messages',
      'lgpd_consents',
      'lgpd_processing_logs',
    ] as const) {
      await this.safeRun(col, async () => {
        counts[col] = await this.deleteDocs(
          this.db.collection(col).where('userId', '==', userId),
        )
      })
    }

    // --- lgpd_data_subject_requests — update status only
    await this.safeRun('lgpd_data_subject_requests', async () => {
      counts.lgpd_data_subject_requests = await this.updateDocs(
        this.db.collection('lgpd_data_subject_requests').where('userId', '==', userId),
        {
          status: 'concluido',
          completedAt: new Date(),
          handledBy: 'account_deletion_automated',
        },
      )
    })

    // --- orders (as cliente — anonymize)
    await this.safeRun('orders_cliente', async () => {
      const snap = await this.db.collection('orders').where('userId', '==', userId).get()
      if (!snap.empty) {
        counts.orders_cliente = await this.anonymizeDocsFromRefs(snap.docs.map((d) => d.ref), {
          clienteNome: 'Usuário Excluído',
          telefone: null,
          endereco: null,
          cpf: null,
          email: null,
          anonymized: true,
          anonymizedAt: new Date(),
        })

        // Anonymize order messages sent by this user
        await this.safeRun('orders_messages', async () => {
          let msgCount = 0
          for (const orderDoc of snap.docs) {
            const msgSnap = await this.db
              .collection('orders')
              .doc(orderDoc.id)
              .collection('messages')
              .where('senderId', '==', userId)
              .get()
            if (!msgSnap.empty) {
              msgCount += await this.anonymizeDocsFromRefs(
                msgSnap.docs.map((d) => d.ref),
                { senderName: 'Usuário Excluído', senderId: 'deleted' },
              )
            }
          }
          counts.orders_messages = msgCount
        })
      }
    })

    // --- orders (as prestador — anonymize prestador fields)
    await this.safeRun('orders_prestador', async () => {
      const snap = await this.db
        .collection('orders')
        .where('prestadorId', '==', userId)
        .get()
      if (!snap.empty) {
        counts.orders_prestador = await this.anonymizeDocsFromRefs(
          snap.docs.map((d) => d.ref),
          {
            'prestador.nome': 'Prestador Excluído',
            'prestador.email': null,
            prestadorId: 'deleted',
            anonymized: true,
            anonymizedAt: new Date(),
          },
        )
      }
    })

    // --- chatConversations — remove userId from participants
    await this.safeRun('chatConversations', async () => {
      const snap = await this.db
        .collection('chatConversations')
        .where('participants', 'array-contains', userId)
        .get()
      if (!snap.empty) {
        let updated = 0
        for (let i = 0; i < snap.docs.length; i += 499) {
          const batch = this.db.batch()
          snap.docs.slice(i, i + 499).forEach((doc) => {
            const participants: string[] = (doc.data().participants ?? []).map(
              (p: string) => (p === userId ? 'deleted_user' : p),
            )
            batch.update(doc.ref, { participants })
          })
          await batch.commit()
          updated += snap.docs.slice(i, i + 499).length
        }
        counts.chatConversations = updated
      }
    })

    // --- chatMessages — anonymize sender
    await this.safeRun('chatMessages', async () => {
      counts.chatMessages = await this.updateDocs(
        this.db.collection('chatMessages').where('senderId', '==', userId),
        { senderName: 'Usuário Excluído', senderId: 'deleted' },
      )
    })

    // --- transactions — anonymize
    await this.safeRun('transactions', async () => {
      counts.transactions = await this.updateDocs(
        this.db.collection('transactions').where('userId', '==', userId),
        { userName: 'Usuário Excluído', userEmail: null, anonymized: true },
      )
    })

    // --- provider_payments — anonymize
    await this.safeRun('provider_payments', async () => {
      counts.provider_payments = await this.updateDocs(
        this.db.collection('provider_payments').where('providerId', '==', userId),
        { providerName: 'Prestador Excluído', providerEmail: null, anonymized: true },
      )
    })

    // --- pagarme_customers — anonymize by email
    if (userEmail) {
      await this.safeRun('pagarme_customers', async () => {
        counts.pagarme_customers = await this.updateDocs(
          this.db.collection('pagarme_customers').where('email', '==', userEmail),
          {
            name: 'Usuário Excluído',
            email: 'deleted@removed.local',
            phone: null,
            document: null,
            anonymized: true,
          },
        )
      })
    }

    return counts
  }

  // ─── Phase 4: LGPD Log ───────────────────────────────────────────────────────

  private async logLgpdDeletion(
    userId: string,
    userEmail: string,
    deletedResources: Record<string, number>,
  ): Promise<void> {
    await this.db.collection('lgpd_processing_logs').add({
      activity: 'exclusao_usuario',
      dataType: [
        'dados_pessoais',
        'documentos',
        'pedidos',
        'mensagens',
        'financeiro',
        'localizacao',
      ],
      legalBasis: 'consentimento',
      purpose: 'Exclusão total da conta solicitada pelo titular dos dados',
      userId,
      userEmail: userEmail || 'deleted',
      metadata: { deletedResources },
      timestamp: new Date(),
    })
  }

  // ─── Batch Helpers ───────────────────────────────────────────────────────────

  private async deleteDocs(query: admin.firestore.Query): Promise<number> {
    const snap = await query.get()
    if (snap.empty) return 0
    return this.deleteDocsFromRefs(snap.docs.map((d) => d.ref))
  }

  private async deleteDocsFromRefs(refs: admin.firestore.DocumentReference[]): Promise<number> {
    let total = 0
    for (let i = 0; i < refs.length; i += 499) {
      const batch = this.db.batch()
      const chunk = refs.slice(i, i + 499)
      chunk.forEach((ref) => batch.delete(ref))
      await batch.commit()
      total += chunk.length
    }
    return total
  }

  private async updateDocs(
    query: admin.firestore.Query,
    data: Record<string, unknown>,
  ): Promise<number> {
    const snap = await query.get()
    if (snap.empty) return 0
    return this.anonymizeDocsFromRefs(snap.docs.map((d) => d.ref), data)
  }

  private async anonymizeDocsFromRefs(
    refs: admin.firestore.DocumentReference[],
    data: Record<string, unknown>,
  ): Promise<number> {
    let total = 0
    for (let i = 0; i < refs.length; i += 499) {
      const batch = this.db.batch()
      const chunk = refs.slice(i, i + 499)
      chunk.forEach((ref) => batch.update(ref, data as admin.firestore.UpdateData<admin.firestore.DocumentData>))
      await batch.commit()
      total += chunk.length
    }
    return total
  }

  private async safeRun(label: string, fn: () => Promise<void>): Promise<void> {
    try {
      await fn()
    } catch (e: unknown) {
      console.error(`[Firestore][${label}] failed:`, e)
    }
  }
}

function toMessage(e: unknown): string {
  if (e instanceof Error) return e.message
  return String(e)
}
