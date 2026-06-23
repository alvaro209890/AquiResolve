import { NextRequest, NextResponse } from 'next/server'
import * as admin from 'firebase-admin'
import { adminApp, getAdminAuth, getAdminFirestore } from '@/lib/firebase-admin'
import { requireMasterSession } from '@/lib/server/master-session'

export async function PUT(
  request: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params
  try {
    const denied = await requireMasterSession(request)
    if (denied) return denied
    console.log('🔐 Iniciando alteração de senha do usuário:', id)

    if (!adminApp) {
      console.error('❌ Firebase Admin não inicializado')
      return NextResponse.json({ 
        success: false, 
        error: 'Firebase Admin não inicializado' 
      }, { status: 500 })
    }

    const body = await request.json()
    const { newPassword } = body || {}

    if (!newPassword) {
      console.error('❌ Nova senha não fornecida')
      return NextResponse.json({ 
        success: false, 
        error: 'Nova senha é obrigatória' 
      }, { status: 400 })
    }

    if (newPassword.length < 6) {
      console.error('❌ Senha muito curta')
      return NextResponse.json({ 
        success: false, 
        error: 'A senha deve ter pelo menos 6 caracteres' 
      }, { status: 400 })
    }

    let auth: admin.auth.Auth
    let db: admin.firestore.Firestore

    try {
      auth = getAdminAuth()
      db = getAdminFirestore()
    } catch (error: any) {
      console.error('❌ Erro ao obter Firebase Admin:', error.message)
      return NextResponse.json({ 
        success: false, 
        error: 'Firebase Admin não inicializado. Verifique se FIREBASE_SERVICE_ACCOUNT está configurado no .env.local' 
      }, { status: 500 })
    }

    // Verificar se o usuário existe
    try {
      const userRecord = await auth.getUser(id)
      console.log('✅ Usuário encontrado:', userRecord.email)
    } catch (error: any) {
      if (error.code === 'auth/user-not-found') {
        console.error('❌ Usuário não encontrado:', id)
        return NextResponse.json({
          success: false,
          error: 'Usuário não encontrado'
        }, { status: 404 })
      }
      throw error
    }

    // Atualizar a senha do usuário
    console.log('🔑 Atualizando senha do usuário...')
    await auth.updateUser(id, {
      password: newPassword
    })

    // Atualizar timestamp de última alteração de senha no Firestore
    console.log('💾 Atualizando timestamp no Firestore...')
    const usuarioRef = db.collection('adminmaster').doc('master').collection('usuarios').doc(id)
    await usuarioRef.update({
      senhaAlteradaEm: admin.firestore.FieldValue.serverTimestamp(),
    })

    console.log('✅ Senha alterada com sucesso!')
    return NextResponse.json({ 
      success: true, 
      message: 'Senha alterada com sucesso' 
    })

  } catch (error: any) {
    console.error('❌ Erro ao alterar senha:', error)
    return NextResponse.json({ 
      success: false, 
      error: error.message || 'Erro interno do servidor' 
    }, { status: 500 })
  }
}
