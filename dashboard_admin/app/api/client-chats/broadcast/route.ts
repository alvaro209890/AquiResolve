import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, adminApp } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

const BATCH_LIMIT = 400 // Firestore batch limit é 500; deixamos margem
const TRUNCATE_PREVIEW = 120

interface ClientUser {
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
): Promise<ClientUser[]> {
  if (audience === 'specific') {
    if (!userIds || userIds.length === 0) return []
    const out: ClientUser[] = []
    for (const uid of userIds) {
      const tokenSnap = await db.collection('userTokens').doc(uid).get()
      out.push({ uid, fcmToken: tokenSnap.data()?.token ?? tokenSnap.data()?.fcmToken })
    }
    return out
  }

  // Filtra apenas usuários com role cliente (role === 'cliente' | 'client')
  const usersSnap = await db.collection('users').get()
  const clients = usersSnap.docs.filter((d) => {
    const role = (d.data().role ?? '').toString().toLowerCase()
    if (audience === 'active' && d.data().isActive === false) return false
    return role === 'cliente' || role === 'client' || role === ''
  })

  // resolve tokens em batch
  const result: ClientUser[] = []
  for (const doc of clients) {
    const tokenSnap = await db.collection('userTokens').doc(doc.id).get()
    result.push({ uid: doc.id, fcmToken: tokenSnap.data()?.token ?? tokenSnap.data()?.fcmToken })
  }
  return result
}

// POST /api/client-chats/broadcast
// Body: { text, type?, audience: 'all'|'active'|'specific', userIds?: string[], adminId?, adminName? }
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
      return NextResponse.json(
        { success: false, error: 'Nenhum destinatário elegível' },
        { status: 400 }
      )
    }

    const preview = text.length > TRUNCATE_PREVIEW ? `${text.substring(0, TRUNCATE_PREVIEW)}…` : text
    const broadcastRef = db.collection('client_chat_broadcasts').doc()
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

    // Fan-out em chunks (cada chunk = 1 batch commit)
    const chunks = chunk(audienceUsers, Math.floor(BATCH_LIMIT / 2)) // 2 writes por destinatário
    let writes = 0
    for (const c of chunks) {
      const batch = db.batch()
      for (const user of c) {
        const chatRef = db.collection('client_chats').doc(user.uid)
        const msgRef = chatRef.collection('messages').doc()
        batch.set(msgRef, {
          text,
          type,
          senderType: 'admin',
          senderId: body.adminId ?? 'admin',
          senderName: body.adminName ?? 'Central AquiResolve',
          broadcastId,
          readByClient: false,
          readByAdmin: true,
          createdAt: now,
        })
        batch.set(
          chatRef,
          {
            clientId: user.uid,
            lastMessage: preview,
            lastMessageAt: now,
            lastSender: 'admin',
            unreadByClient: admin.firestore.FieldValue.increment(1),
            archived: false,
            updatedAt: now,
          },
          { merge: true }
        )
      }
      await batch.commit()
      writes += c.length
    }

    // Notificações no sino (em paralelo, best-effort, em chunks de 400)
    for (const c of chunks) {
      const batch = db.batch()
      for (const user of c) {
        const notifRef = db.collection('notifications').doc()
        batch.set(notifRef, {
          userId: user.uid,
          title: 'Central AquiResolve',
          message: preview,
          type: 'central_message',
          isRead: false,
          timestamp: now,
        })
      }
      await batch.commit().catch(() => null)
    }

    // FCM em paralelo (best-effort, em chunks de 500 tokens)
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
            data: { type: 'central_message', broadcastId },
          })
          fcmSent += result.successCount
          fcmFailed += result.failureCount
        } catch (err) {
          console.warn('[client-chats broadcast] FCM falhou em um chunk:', err)
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
    console.error('[client-chats broadcast] erro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
