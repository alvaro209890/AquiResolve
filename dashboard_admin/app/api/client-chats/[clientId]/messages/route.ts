import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, adminApp } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

const TRUNCATE_PREVIEW = 120

interface RouteCtx {
  params: Promise<{ clientId: string }>
}

// GET /api/client-chats/[clientId]/messages?limit=100
export async function GET(request: NextRequest, ctx: RouteCtx) {
  try {
    await requireAdminPermission(request, 'controle')
    const { clientId } = await ctx.params
    const db = getAdminFirestore()
    const { searchParams } = new URL(request.url)
    const limit = Math.min(parseInt(searchParams.get('limit') ?? '200', 10), 500)

    const snap = await db
      .collection('client_chats')
      .doc(clientId)
      .collection('messages')
      .orderBy('createdAt', 'desc')
      .limit(limit)
      .get()

    const messages = snap.docs.map((d) => ({ id: d.id, ...d.data() })).reverse()

    return NextResponse.json({ success: true, messages })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}

// POST /api/client-chats/[clientId]/messages
// Body: { text, type?, relatedOrderId?, adminId?, adminName? }
export async function POST(request: NextRequest, ctx: RouteCtx) {
  try {
    await requireAdminPermission(request, 'controle')
    const { clientId } = await ctx.params
    const db = getAdminFirestore()
    const body = (await request.json()) as {
      text: string
      type?: 'text' | 'promotion' | 'notice' | 'order_update'
      relatedOrderId?: string
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

    const type = body.type ?? 'text'
    const now = admin.firestore.FieldValue.serverTimestamp()
    const preview = text.length > TRUNCATE_PREVIEW ? `${text.substring(0, TRUNCATE_PREVIEW)}…` : text

    // Garante o doc do chat (cria com metadata caso seja a primeira interação)
    const chatRef = db.collection('client_chats').doc(clientId)
    const chatSnap = await chatRef.get()
    if (!chatSnap.exists) {
      const clientSnap = await db.collection('users').doc(clientId).get()
      const clientName = clientSnap.data()?.fullName ?? clientSnap.data()?.name ?? clientSnap.data()?.displayName ?? 'Cliente'
      const clientEmail = clientSnap.data()?.email ?? ''
      await chatRef.set({
        clientId,
        clientName,
        clientEmail,
        archived: false,
        pinned: false,
        unreadByAdmin: 0,
        unreadByClient: 0,
        createdAt: now,
        updatedAt: now,
      })
    }

    const msgRef = chatRef.collection('messages').doc()
    const msgData: Record<string, unknown> = {
      text,
      type,
      senderType: 'admin',
      senderId: body.adminId ?? 'admin',
      senderName: body.adminName ?? 'Central AquiResolve',
      readByClient: false,
      readByAdmin: true,
      createdAt: now,
    }
    if (body.relatedOrderId) {
      msgData.relatedOrderId = body.relatedOrderId
    }
    await msgRef.set(msgData)

    await chatRef.set(
      {
        lastMessage: preview,
        lastMessageAt: now,
        lastSender: 'admin',
        unreadByClient: admin.firestore.FieldValue.increment(1),
        updatedAt: now,
      },
      { merge: true }
    )

    // FCM (best-effort) — não falha o request se não houver token
    try {
      if (adminApp) {
        const tokenSnap = await db.collection('userTokens').doc(clientId).get()
        const fcmToken = tokenSnap.data()?.token || tokenSnap.data()?.fcmToken
        if (fcmToken) {
          await admin.messaging(adminApp).send({
            token: fcmToken,
            notification: {
              title: 'Central AquiResolve',
              body: preview,
            },
            data: {
              type: 'central_message',
              clientId,
              messageId: msgRef.id,
            },
          })
        }
      }
    } catch (err) {
      console.warn('[client-chats] FCM falhou:', err)
    }

    // Persiste no sino (mesma lógica das outras notificações)
    await db.collection('notifications').add({
      userId: clientId,
      title: 'Central AquiResolve',
      message: preview,
      type: 'central_message',
      isRead: false,
      timestamp: now,
    })

    return NextResponse.json({ success: true, messageId: msgRef.id })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    console.error('[client-chats POST] erro:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
