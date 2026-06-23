import { 
  doc, 
  getDoc, 
  setDoc, 
  collection, 
  getDocs, 
  addDoc, 
  updateDoc, 
  deleteDoc,
  query,
  where,
  orderBy
} from "firebase/firestore"
import bcrypt from "bcryptjs"
import { db } from "@/lib/firebase"
import type { AdminPermissions } from "@/lib/admin-permissions"

const BCRYPT_ROUNDS = 10

export interface AdminMaster {
  id: string
  email: string
  senhaHash: string
  nome: string
  permissoes: AdminPermissions
}

export interface MasterUser {
  id: string
  email: string
  nome: string
  permissoes: AdminPermissions
}

export class AdminMasterService {
  // Hash de senha com bcrypt (seguro, one-way)
  static async hashPassword(password: string): Promise<string> {
    return bcrypt.hash(password, BCRYPT_ROUNDS)
  }

  // Comparar senha: bcrypt (novo) ou base64 (legado)
  private static async comparePassword(password: string, senhaHash: string): Promise<boolean> {
    if (!senhaHash || typeof senhaHash !== 'string') {
      return false
    }
    if (senhaHash.startsWith('$2')) {
      return bcrypt.compare(password, senhaHash)
    }
    // Legado: senha em base64
    try {
      const base64 =
        typeof btoa !== 'undefined'
          ? btoa(unescape(encodeURIComponent(password)))
          : Buffer.from(password, 'utf8').toString('base64')
      return base64 === senhaHash
    } catch {
      return false
    }
  }

  // Verificar credenciais do AdminMaster
  static async authenticateMaster(email: string, password: string): Promise<AdminMaster | null> {
    try {
      const adminMasterRef = doc(db, 'adminmaster', 'master')
      const adminMasterDoc = await getDoc(adminMasterRef)
      
      if (!adminMasterDoc.exists()) {
        throw new Error('MASTER_NOT_FOUND')
      }

      const adminData = adminMasterDoc.data() as AdminMaster
      if (adminData.email !== email) {
        throw new Error('Credenciais inválidas')
      }

      const senhaHash = adminData.senhaHash
      if (!senhaHash || typeof senhaHash !== 'string') {
        throw new Error('MASTER_INVALID_CONFIG')
      }

      const passwordMatch = await this.comparePassword(password, senhaHash)
      if (!passwordMatch) {
        throw new Error('Credenciais inválidas')
      }

      return {
        ...adminData,
        id: adminMasterDoc.id
      }
    } catch (error: unknown) {
      const err = error as { code?: string; message?: string }
      console.error('Erro na autenticação master:', err)
      const code = err?.code ?? ''
      if (String(code).includes('permission-denied')) {
        throw new Error('PERMISSION_DENIED')
      }
      throw error
    }
  }

  // Criar AdminMaster inicial
  static async createAdminMaster(data: Omit<AdminMaster, 'id'>): Promise<void> {
    try {
      const adminMasterRef = doc(db, 'adminmaster', 'master')
      await setDoc(adminMasterRef, data)
    } catch (error) {
      console.error('Erro ao criar AdminMaster:', error)
      throw error
    }
  }

  // Buscar AdminMaster
  static async getAdminMaster(): Promise<AdminMaster | null> {
    try {
      const adminMasterRef = doc(db, 'adminmaster', 'master')
      const adminMasterDoc = await getDoc(adminMasterRef)

      if (!adminMasterDoc.exists()) {
        return null
      }

      return {
        id: adminMasterDoc.id,
        ...adminMasterDoc.data()
      } as AdminMaster
    } catch (error: unknown) {
      const code = (error as { code?: string })?.code ?? ''
      // adminmaster/master is intentionally blocked for client reads (Admin SDK only).
      // Return null silently so callers fall through to the sub-user check.
      if (String(code).includes('permission-denied')) {
        return null
      }
      console.error('Erro ao buscar AdminMaster:', error)
      return null
    }
  }

  // Listar usuários da subcoleção via Admin SDK (client SDK bloqueado pelas Firestore rules)
  static async getUsuarios(_adminId: string): Promise<MasterUser[]> {
    try {
      const res = await fetch('/api/adminmaster/users', { cache: 'no-store' })
      const data = await res.json()
      if (!res.ok || !data.success) throw new Error(data.error || 'Erro ao buscar usuários')
      return data.usuarios as MasterUser[]
    } catch (error) {
      console.error('Erro ao buscar usuários:', error)
      throw error
    }
  }

  // Adicionar usuário (fluxo real usa POST /api/adminmaster/users direto do dashboard)
  static async addUsuario(adminId: string, usuario: Omit<MasterUser, 'id'>): Promise<string> {
    try {
      const usuariosRef = collection(db, 'adminmaster', adminId, 'usuarios')
      const docRef = await addDoc(usuariosRef, usuario)
      return docRef.id
    } catch (error) {
      console.error('Erro ao adicionar usuário:', error)
      throw error
    }
  }

  // Atualizar usuário via Admin SDK
  static async updateUsuario(_adminId: string, usuarioId: string, data: Partial<MasterUser> | MasterUser['permissoes']): Promise<void> {
    try {
      const isPermissionsOnly =
        data &&
        !('email' in (data as any)) &&
        !('nome' in (data as any)) &&
        !('permissoes' in (data as any))
      const body = isPermissionsOnly ? { permissoes: data } : data
      const res = await fetch(`/api/adminmaster/users?id=${encodeURIComponent(usuarioId)}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      })
      const result = await res.json()
      if (!res.ok || !result.success) throw new Error(result.error || 'Erro ao atualizar usuário')
    } catch (error) {
      console.error('Erro ao atualizar usuário:', error)
      throw error
    }
  }

  // Deletar usuário via Admin SDK
  static async deleteUsuario(_adminId: string, usuarioId: string): Promise<void> {
    try {
      const res = await fetch(`/api/adminmaster/users?id=${encodeURIComponent(usuarioId)}`, {
        method: 'DELETE',
      })
      const result = await res.json()
      if (!res.ok || !result.success) throw new Error(result.error || 'Erro ao deletar usuário')
    } catch (error) {
      console.error('Erro ao deletar usuário:', error)
      throw error
    }
  }

  // Buscar usuário por email
  static async getUsuarioByEmail(email: string): Promise<MasterUser | null> {
    try {
      const usuariosRef = collection(db, 'adminmaster', 'master', 'usuarios')
      const q = query(usuariosRef, where('email', '==', email))
      const querySnapshot = await getDocs(q)
      
      if (querySnapshot.empty) {
        return null
      }

      const doc = querySnapshot.docs[0]
      const data = doc.data()
      
      return {
        id: doc.id,
        email: data.email,
        nome: data.nome,
        permissoes: data.permissoes
      }
    } catch (error: unknown) {
      const code = (error as { code?: string })?.code ?? ''
      if (String(code).includes('permission-denied')) {
        return null
      }
      console.error('Erro ao buscar usuário por email:', error)
      return null
    }
  }

  // Verificar se email já existe
  static async emailExists(email: string): Promise<boolean> {
    try {
      const usuario = await this.getUsuarioByEmail(email)
      return usuario !== null
    } catch (error) {
      console.error('Erro ao verificar email:', error)
      return false
    }
  }

  // Atualizar senha do AdminMaster
  static async updateMasterPassword(newPassword: string): Promise<void> {
    try {
      const hashedPassword = await this.hashPassword(newPassword)
      const adminMasterRef = doc(db, 'adminmaster', 'master')
      await updateDoc(adminMasterRef, { senhaHash: hashedPassword })
    } catch (error) {
      console.error('Erro ao atualizar senha master:', error)
      throw error
    }
  }

  // Verificar se AdminMaster existe
  static async masterExists(): Promise<boolean> {
    try {
      const adminMaster = await this.getAdminMaster()
      return adminMaster !== null
    } catch (error) {
      console.error('Erro ao verificar se master existe:', error)
      return false
    }
  }
}
