import { NextRequest, NextResponse } from 'next/server'
import * as admin from 'firebase-admin'
import { adminApp, getAdminAuth, getAdminFirestore } from '@/lib/firebase-admin'
import { normalizeAdminPermissions } from '@/lib/admin-permissions'
import { requireMasterSession } from '@/lib/server/master-session'

export async function GET(req: NextRequest) {
  try {
    const denied = await requireMasterSession(req)
    if (denied) return denied
    let db: admin.firestore.Firestore
    try {
      db = getAdminFirestore()
    } catch {
      return NextResponse.json({ success: false, error: 'Firebase Admin não inicializado' }, { status: 500 })
    }
    const snapshot = await db.collection('adminmaster').doc('master').collection('usuarios').get()
    const usuarios = snapshot.docs.map(doc => ({
      id: doc.id,
      email: doc.data().email ?? '',
      nome: doc.data().nome ?? '',
      permissoes: normalizeAdminPermissions(doc.data().permissoes, { inheritLegacy: true }),
      ativo: doc.data().ativo !== false && doc.data().active !== false,
    }))
    return NextResponse.json({ success: true, usuarios })
  } catch (error: any) {
    console.error('❌ Erro ao listar usuários master:', error)
    return NextResponse.json({ success: false, error: error?.message || 'Erro interno' }, { status: 500 })
  }
}

export async function PATCH(req: NextRequest) {
  try {
    const denied = await requireMasterSession(req)
    if (denied) return denied
    const { searchParams } = new URL(req.url)
    const id = searchParams.get('id')
    if (!id) return NextResponse.json({ success: false, error: 'ID obrigatório' }, { status: 400 })

    let db: admin.firestore.Firestore
    try {
      db = getAdminFirestore()
    } catch {
      return NextResponse.json({ success: false, error: 'Firebase Admin não inicializado' }, { status: 500 })
    }

    const body = await req.json()
    const update: Record<string, unknown> = {}
    if (body?.permissoes) {
      update.permissoes = normalizeAdminPermissions(body.permissoes, { inheritLegacy: false })
    }
    if (typeof body?.ativo === 'boolean') update.ativo = body.ativo
    if (typeof body?.nome === 'string' && body.nome.trim()) update.nome = body.nome.trim()
    if (Object.keys(update).length === 0) {
      return NextResponse.json({ success: false, error: 'Nenhuma alteração válida' }, { status: 400 })
    }
    update.atualizadoEm = admin.firestore.FieldValue.serverTimestamp()
    const auth = getAdminAuth()
    await db.collection('adminmaster').doc('master').collection('usuarios').doc(id).update(update)
    if (update.permissoes) {
      const authUser = await auth.getUser(id)
      await auth.setCustomUserClaims(id, {
        ...(authUser.customClaims ?? {}),
        admin: true,
        role: 'admin',
        permissions: update.permissoes,
      })
    }
    if (typeof update.ativo === 'boolean') {
      await auth.updateUser(id, { disabled: update.ativo === false })
    }
    return NextResponse.json({ success: true })
  } catch (error: any) {
    console.error('❌ Erro ao atualizar usuário master:', error)
    return NextResponse.json({ success: false, error: error?.message || 'Erro interno' }, { status: 500 })
  }
}

export async function DELETE(req: NextRequest) {
  try {
    const denied = await requireMasterSession(req)
    if (denied) return denied
    const { searchParams } = new URL(req.url)
    const id = searchParams.get('id')
    if (!id) return NextResponse.json({ success: false, error: 'ID obrigatório' }, { status: 400 })

    let db: admin.firestore.Firestore
    try {
      db = getAdminFirestore()
    } catch {
      return NextResponse.json({ success: false, error: 'Firebase Admin não inicializado' }, { status: 500 })
    }

    await Promise.all([
      db.collection('adminmaster').doc('master').collection('usuarios').doc(id).delete(),
      getAdminAuth().deleteUser(id).catch((error: { code?: string }) => {
        if (error?.code !== 'auth/user-not-found') throw error
      }),
    ])
    return NextResponse.json({ success: true })
  } catch (error: any) {
    console.error('❌ Erro ao deletar usuário master:', error)
    return NextResponse.json({ success: false, error: error?.message || 'Erro interno' }, { status: 500 })
  }
}

export async function POST(req: NextRequest) {
  try {
    const denied = await requireMasterSession(req)
    if (denied) return denied
    console.log('🚀 Iniciando criação de usuário master...')
    console.log('🔍 Firebase Admin Status:', { 
      app: !!adminApp, 
      serviceAccount: !!process.env.FIREBASE_SERVICE_ACCOUNT,
      projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID 
    })
    
    const body = await req.json()
    const { nome, email, password, permissoes } = body || {}

    console.log('📝 Dados recebidos:', { nome, email, permissoes: Object.keys(permissoes || {}) })

    if (!nome || !email || !password || !permissoes) {
      console.error('❌ Dados inválidos:', { nome: !!nome, email: !!email, password: !!password, permissoes: !!permissoes })
      return NextResponse.json({ success: false, error: 'Dados inválidos' }, { status: 400 })
    }

    let auth: admin.auth.Auth
    let db: admin.firestore.Firestore

    try {
      auth = getAdminAuth()
      db = getAdminFirestore()
      console.log('✅ Firebase Admin disponível, criando usuário...')
    } catch (error: any) {
      console.error('❌ Erro ao obter Firebase Admin:', error.message)
      console.error('❌ Verifique se FIREBASE_SERVICE_ACCOUNT está configurado no .env.local')
      return NextResponse.json({ 
        success: false, 
        error: 'Firebase Admin não inicializado. Verifique se FIREBASE_SERVICE_ACCOUNT está configurado no .env.local' 
      }, { status: 500 })
    }

    // Verifica se o usuário já existe
    try {
      const existingUser = await auth.getUserByEmail(email)
      if (existingUser) {
        console.error('❌ Usuário já existe:', email)
        return NextResponse.json({ success: false, error: 'Usuário já existe com este email' }, { status: 400 })
      }
    } catch (error: any) {
      if (error.code !== 'auth/user-not-found') {
        console.error('❌ Erro ao verificar usuário existente:', error)
        throw error
      }
      // Usuário não existe, continuar
    }

    // Cria usuário de autenticação
    console.log('👤 Criando usuário no Firebase Auth...')
    const userRecord = await auth.createUser({
      email,
      password,
      displayName: nome,
      emailVerified: false,
      disabled: false,
    })

    console.log('✅ Usuário criado no Auth:', userRecord.uid)

    // Salva permissões na subcoleção adminmaster/master/usuarios usando uid como id
    console.log('💾 Salvando permissões no Firestore...')
    const usuarioRef = db.collection('adminmaster').doc('master').collection('usuarios').doc(userRecord.uid)
    const normalizedPermissions = normalizeAdminPermissions(permissoes, { inheritLegacy: false })
    try {
      await Promise.all([
        usuarioRef.set({
          nome: String(nome).trim(),
          email: String(email).trim().toLowerCase(),
          ativo: true,
          permissoes: normalizedPermissions,
          criadoEm: admin.firestore.FieldValue.serverTimestamp(),
        }),
        auth.setCustomUserClaims(userRecord.uid, {
          admin: true,
          role: 'admin',
          permissions: normalizedPermissions,
        }),
      ])
    } catch (saveError) {
      await auth.deleteUser(userRecord.uid).catch(() => undefined)
      throw saveError
    }

    console.log('✅ Usuário master criado com sucesso!')
    return NextResponse.json({ success: true, uid: userRecord.uid })
  } catch (error: any) {
    console.error('❌ Erro ao criar usuário master:', error)
    
    // Tratamento específico para erros comuns
    if (error.code === 'auth/email-already-exists') {
      return NextResponse.json({ success: false, error: 'Email já está em uso' }, { status: 400 })
    }
    if (error.code === 'auth/invalid-email') {
      return NextResponse.json({ success: false, error: 'Email inválido' }, { status: 400 })
    }
    if (error.code === 'auth/weak-password') {
      return NextResponse.json({ success: false, error: 'Senha muito fraca' }, { status: 400 })
    }
    
    return NextResponse.json({ success: false, error: error?.message || 'Erro interno' }, { status: 500 })
  }
}
