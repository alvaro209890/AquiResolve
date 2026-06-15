import { NextRequest, NextResponse } from 'next/server'
import { LGPDService } from '@/lib/services/lgpd-service'
import type { DataSubjectRight } from '@/types/lgpd'
import { getFirestore } from 'firebase-admin/firestore'
import { adminApp } from '@/lib/firebase-admin'

/**
 * POST /api/lgpd/rights
 * Criar solicitação de direito do titular
 */
export async function POST(req: NextRequest) {
  try {
    const body = await req.json()
    const { userId, userEmail, requestType, description } = body

    if (!userId || !userEmail || !requestType) {
      return NextResponse.json(
        { success: false, error: 'Dados obrigatórios ausentes' },
        { status: 400 }
      )
    }

    const requestId = await LGPDService.createDataSubjectRequest(
      userId,
      userEmail,
      requestType as DataSubjectRight,
      description
    )

    return NextResponse.json({
      success: true,
      message: 'Solicitação criada com sucesso',
      requestId,
    })
  } catch (error: any) {
    console.error('Erro ao criar solicitação:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao criar solicitação',
        details: error.message,
      },
      { status: 500 }
    )
  }
}

/**
 * GET /api/lgpd/rights
 * Listar solicitações do usuário
 */
export async function GET(req: NextRequest) {
  try {
    const searchParams = req.nextUrl.searchParams
    const userId = searchParams.get('userId')

    if (!userId) {
      return NextResponse.json(
        { success: false, error: 'userId é obrigatório' },
        { status: 400 }
      )
    }

    const requests = await LGPDService.getUserRequests(userId)

    return NextResponse.json({
      success: true,
      requests,
    })
  } catch (error: any) {
    console.error('Erro ao buscar solicitações:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao buscar solicitações',
        details: error.message,
      },
      { status: 500 }
    )
  }
}

/**
 * GET /api/lgpd/rights/access
 * Obter todos os dados pessoais do usuário (direito de acesso)
 */
async function GET_ACCESS(req: NextRequest) {
  try {
    const searchParams = req.nextUrl.searchParams
    const userId = searchParams.get('userId')

    if (!userId) {
      return NextResponse.json(
        { success: false, error: 'userId é obrigatório' },
        { status: 400 }
      )
    }

    if (!adminApp) {
      return NextResponse.json(
        { success: false, error: 'Firebase Admin não inicializado' },
        { status: 500 }
      )
    }
    const db = getFirestore(adminApp)

    // Buscar todos os dados do usuário em diferentes coleções
    const userData: any = {
      userId,
      collectedAt: new Date().toISOString(),
      data: {},
    }

    // Dados de usuário
    const userDoc = await db.collection('users').doc(userId).get()
    if (userDoc.exists) {
      const userData_raw = userDoc.data()
      // Remover senha e dados sensíveis antes de retornar
      const { password, senhaHash, ...safeUserData } = userData_raw || {}
      userData.data.user = safeUserData
    }

    // Dados de pedidos
    const ordersSnapshot = await db
      .collection('orders')
      .where('userId', '==', userId)
      .get()
    userData.data.orders = ordersSnapshot.docs.map((doc) => ({
      id: doc.id,
      ...doc.data(),
    }))

    // Dados de consentimentos
    const consents = await LGPDService.getUserConsents(userId)
    userData.data.consents = consents

    // Logs de processamento
    const processingLogs = await LGPDService.getUserProcessingLogs(userId)
    userData.data.processingLogs = processingLogs

    // Solicitações anteriores
    const requests = await LGPDService.getUserRequests(userId)
    userData.data.requests = requests

    return NextResponse.json({
      success: true,
      data: userData,
    })
  } catch (error: any) {
    console.error('Erro ao obter dados do usuário:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao obter dados do usuário',
        details: error.message,
      },
      { status: 500 }
    )
  }
}
