import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, adminApp } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

// Broadcast Base → Prestadores (espelha /api/client-chats/broadcast).
// Resolve a audiência na coleção `providers`, grava em `provider_chats` + sino + FCM.

const BATCH_LIMIT = 400
const TRUNCATE_PREVIEW = 120

interface ProviderUser {
  uid: string
  fcmToken?: string
}

function chunk<T>(arr: T[], size: number): T[][] {
  const out: T[][] = []
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size))
  return out
}

async function resolveAudience(
  audience: 'all' | 'active' | 'specific',
  userIds: string[] | undefined,
  db: admin.firestore.Firestore
): Promise<ProviderUser[]> {
  if (audience === 'specific') {
    if (!userIds || userIds.length === 0) return []
    const out: ProviderUser[] = []
    for (const uid of userIds) {
      const tokenSnap = await db.collection('userTokens').doc(uid).get()
      out.push({ uid, fcmToken: tokenSnap.data()?.token ?? tokenSnap.data()?.fcmToken })
    }
    return out
  }

  // Todos os prestadores; 'active' descarta os explicitamente inativos.
  const provSnap = await db.collection('providers').get()
  const providers = provSnap.docs.filter((d) => {
    if (audience === 'active' && d.data().isActive === false) return false
    return true
  })

  const result: ProviderUser[] = []
  for (const doc of providers) {
    const tokenSnap = await db.collection('userTokens').doc(doc.id).get()
    result.push({ uid: doc.id, fcmToken: tokenSnap.data()?.token ?? tokenSnap.data()?.fcmToken })
  }
  return result
}

// POST /api/provider-chats/broadcast
// Body: { text, type?, audience: 'all'|'active'|'specific', userIds?, adminId?, adminName? }
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'controle')
    const db = getAdminFirestore()
    const body = (await request.json()) as {
      text: string
      type?: 'text' | 'promotion' | 'notice' | 'order_update'
      audience: 'all' | 'active' | 'specific'
      userIds?: string[]
      adminId?: string
      adminName?: string
    }

    const text = (body.text ?? '').trim()
    if (!text) {
      return NextResponse.json({ success: false, error: 'text é obrigatório' }, { status: 400 })
    }
    if (text.length > 2000) {
      return NextResponse.json({ success: false, error: 'texto acima de 2000 caracteres' }, { status: 400 })
    }
    if (!['all', 'active', 'specific'].includes(body.audience)) {
      return NextResponse.json({ success: false, error: 'audience inválida' }, { status: 400 })
    }

    const type = body.type ?? 'notice'
    const audienceUsers = await resolveAudience(body.audience, body.userIds, db)
    if (audienceUsers.length === 0) {
      return NextResponse.json({ success: false, error: 'Nenhum destinatário elegível' }, { status: 400 })
    }

    const preview = text.length > TRUNCATE_PREVIEW ? `${text.substring(0, TRUNCATE_PREVIEW)}…` : text
    const broadcastRef = db.collection('provider_chat_broadcasts').doc()
    const broadcastId = broadcastRef.id
    const now = admin.firestore.FieldValue.serverTimestamp()

    await broadcastRef.set({
      id: broadcastId,
      text,
      type,
      audience: body.audience,
      audienceCount: audienceUsers.length,
      sentByAdminId: body.adminId ?? null,
      sentByAdminName: body.adminName ?? null,
      createdAt: now,
    })

    const chunks = chunk(audienceUsers, Math.floor(BATCH_LIMIT / 2))
    let writes = 0
    for (const c of chunks) {
      const batch = db.batch()
      for (const user of c) {
        const chatRef = db.collection('provider_chats').doc(user.uid)
        const msgRef = chatRef.collection('messages').doc()
        batch.set(msgRef, {
          text,
          type,
          senderType: 'admin',
          senderId: body.adminId ?? 'admin',
          senderName: body.adminName ?? 'Central AquiResolve',
          broadcastId,
          readByProvider: false,
          readByAdmin: true,
          createdAt: now,
        })
        batch.set(
          chatRef,
          {
            providerId: user.uid,
            lastMessage: preview,
            lastMessageAt: now,
            lastSender: 'admin',
            unreadByProvider: admin.firestore.FieldValue.increment(1),
            archived: false,
            updatedAt: now,
          },
          { merge: true }
        )
      }
      await batch.commit()
      writes += c.length
    }

    // Sino (best-effort)
    for (const c of chunks) {
      const batch = db.batch()
      for (const user of c) {
        const notifRef = db.collection('notifications').doc()
        batch.set(notifRef, {
          userId: user.uid,
          title: 'Central AquiResolve',
          message: preview,
          type: 'provider_message',
          isRead: false,
          timestamp: now,
        })
      }
      await batch.commit().catch(() => null)
    }

    // FCM (best-effort)
    let fcmSent = 0
    let fcmFailed = 0
    if (adminApp) {
      const tokens = audienceUsers.map((u) => u.fcmToken).filter((t): t is string => !!t)
      const tokenChunks = chunk(tokens, 500)
      for (const tc of tokenChunks) {
        try {
          const result = await admin.messaging(adminApp).sendEachForMulticast({
            tokens: tc,
            notification: { title: 'Central AquiResolve', body: preview },
            data: { type: 'provider_message', broadcastId },
          })
          fcmSent += result.successCount
          fcmFailed += result.failureCount
        } catch (err) {
          console.warn('[provider-chats broadcast] FCM falhou em um chunk:', err)
        }
      }
    }

    return NextResponse.json({
      success: true,
      broadcastId,
      delivered: writes,
      fcm: { sent: fcmSent, failed: fcmFailed },
    })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('[provider-chats broadcast] erro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
