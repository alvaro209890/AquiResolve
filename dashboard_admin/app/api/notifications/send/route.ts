import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore, adminApp } from '@/lib/firebase-admin'
import * as admin from 'firebase-admin'

// POST /api/notifications/send — envia notificação FCM para um usuário ou grupo
export async function POST(request: NextRequest) {
  try {
    if (!adminApp) {
      return NextResponse.json(
        { success: false, error: 'Firebase Admin SDK não configurado' },
        { status: 503 }
      )
    }

    const db = getAdminFirestore()
    const messaging = admin.messaging(adminApp)

    const body = await request.json()
    const {
      userId,      // UID específico (opcional — usa token do userTokens/{userId})
      userIds,     // Array de UIDs (opcional)
      token,       // FCM token direto (opcional)
      tokens,      // Array de tokens (opcional)
      title,
      message: bodyMessage,
      data,        // Dados extras (key/value string)
      topic,       // Tópico FCM (opcional)
    } = body as {
      userId?: string
      userIds?: string[]
      token?: string
      tokens?: string[]
      title: string
      message: string
      data?: Record<string, string>
      topic?: string
    }

    if (!title || !bodyMessage) {
      return NextResponse.json(
        { success: false, error: 'title e message são obrigatórios' },
        { status: 400 }
      )
    }

    const notification = { title, body: bodyMessage }
    const results: { sent: number; failed: number; errors: string[] } = {
      sent: 0,
      failed: 0,
      errors: [],
    }

    // Enviar por tópico
    if (topic) {
      await messaging.send({ notification, topic, data })
      results.sent++
    }

    // Resolver tokens por UIDs
    const resolvedTokens: string[] = token ? [token] : tokens ? [...tokens] : []

    const targetIds = userId ? [userId] : userIds || []
    for (const uid of targetIds) {
      try {
        const tokenSnap = await db.collection('userTokens').doc(uid).get()
        const fcmToken = tokenSnap.data()?.token || tokenSnap.data()?.fcmToken
        if (fcmToken) {
          resolvedTokens.push(fcmToken)
        }
      } catch {
        // ignora se não encontrar token
      }
    }

    // Enviar para tokens individuais
    if (resolvedTokens.length > 0) {
      const sendResult = await messaging.sendEachForMulticast({
        tokens: resolvedTokens,
        notification,
        data,
      })

      results.sent += sendResult.successCount
      results.failed += sendResult.failureCount
      sendResult.responses.forEach((r, i) => {
        if (!r.success && r.error) {
          results.errors.push(`Token[${i}]: ${r.error.message}`)
        }
      })
    }

    if (results.sent === 0 && results.failed === 0 && !topic) {
      return NextResponse.json(
        { success: false, error: 'Nenhum token FCM encontrado para os destinatários informados' },
        { status: 400 }
      )
    }

    return NextResponse.json({
      success: true,
      results,
      message: `Notificação enviada: ${results.sent} sucesso(s), ${results.failed} falha(s)`,
    })
  } catch (error: unknown) {
    const message = error instanceof Error ? error.message : String(error)
    console.error('Erro ao enviar notificação:', message)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
