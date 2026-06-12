"use client"

import { useState, useEffect, useCallback } from 'react'
import { getProviderDocuments, getAllPendingProviders, hasProviderDocuments, invalidatePendingProvidersCache } from '@/lib/storage'
import { useToast } from '@/hooks/use-toast'
import {
  DocumentVerification,
  VerificationStats,
  VerificationFilters,
  type ProviderDocuments,
} from '@/types/verification'
import { db } from '@/lib/firebase'
import { doc, getDoc, getDocs, collection, query, where, updateDoc, addDoc, serverTimestamp, documentId, limit, orderBy } from 'firebase/firestore'
import { extractServiceCategories } from '@/lib/services/firebase-providers'
import { resolveQueueVerificationStatus } from '@/lib/verification-status'

export const useDocumentVerification = () => {
  const [verifications, setVerifications] = useState<DocumentVerification[]>([])
  const [loading, setLoading] = useState(false)
  const [stats, setStats] = useState<VerificationStats>({
    total: 0,
    pending: 0,
    approved: 0,
    rejected: 0,
    totalDocuments: 0,
    documentsByType: {}
  })
  const { toast } = useToast()

  const extractCoordinates = (userData: any): { lat?: number; lng?: number } => {
    if (!userData || typeof userData !== 'object') return {}
    const loc = userData.localizacao
    const latRaw = loc?.lat ?? userData.latitude ?? userData.lat
    const lngRaw = loc?.lng ?? userData.longitude ?? userData.lng
    const lat = typeof latRaw === 'number' ? latRaw : Number(latRaw)
    const lng = typeof lngRaw === 'number' ? lngRaw : Number(lngRaw)

    return {
      lat: Number.isFinite(lat) ? lat : undefined,
      lng: Number.isFinite(lng) ? lng : undefined,
    }
  }

  // Buscar todas as verificações
  const fetchVerifications = useCallback(async () => {
    setLoading(true)
    try {
      // Buscar documentos diretamente do Firebase Storage
      let storageProviders: ProviderDocuments[] = []
      try {
        storageProviders = await getAllPendingProviders()
      } catch (storageError: any) {
        
        // Se for erro de Storage, mostra mensagem específica
        if (storageError?.code?.includes('storage')) {
          toast({
            title: "Erro no Firebase Storage",
            description: "Não foi possível acessar os documentos. Verifique as permissões do Storage.",
            variant: "destructive"
          })
        } else {
          toast({
            title: "Erro",
            description: "Não foi possível carregar os documentos do Storage.",
            variant: "destructive"
          })
        }
        
        // Continua mesmo com erro, mas sem documentos
        storageProviders = []
      }

      const storageIds = new Set(storageProviders.map((p) => p.providerId))
      const mergedFromFirestore: ProviderDocuments[] = []

      if (db) {
        try {
          // Busca TODOS os prestadores — where('verificationStatus', 'in', [...]) não retorna
          // documentos sem o campo definido (novos cadastros), por isso usamos a coleção inteira.
          const [providersSnap, usersSnap] = await Promise.all([
            getDocs(query(collection(db, 'providers'), limit(500))),
            getDocs(query(collection(db, 'users'), where('userType', 'in', ['provider', 'prestador']), limit(500))).catch(() => null),
          ])

          const addIfMissing = (id: string) => {
            if (!storageIds.has(id) && !mergedFromFirestore.some(p => p.providerId === id)) {
              mergedFromFirestore.push({
                providerId: id,
                documents: {},
                uploadedAt: new Date(),
                firstUploadedAt: new Date(),
                status: 'pending',
              })
            }
          }

          providersSnap.forEach(d => addIfMissing(d.id))
          usersSnap?.forEach(d => addIfMissing(d.id))
        } catch (mergeErr) {
          console.warn('Não foi possível mesclar prestadores do Firestore:', mergeErr)
        }
      }

      const allProvidersDocs: ProviderDocuments[] = [...storageProviders, ...mergedFromFirestore]

      if (allProvidersDocs.length === 0) {
        setVerifications([])
        setStats({
          total: 0,
          pending: 0,
          approved: 0,
          rejected: 0,
          totalDocuments: 0,
          documentsByType: {}
        })
        return
      }

      const providerIds = allProvidersDocs.map((p) => p.providerId)

      // Buscar providers e verifications em batch (Firestore limita 'in' a 30 itens)
      const BATCH_SIZE = 30
      const providersMap: Record<string, any> = {}
      const verificationsMap: Record<string, { status: string; submittedAt?: Date; reviewedAt?: Date; createdAt?: Date }> = {}

      for (let i = 0; i < providerIds.length; i += BATCH_SIZE) {
        const batchIds = providerIds.slice(i, i + BATCH_SIZE)
        const [providersSnap, verifSnap] = await Promise.all([
          getDocs(query(collection(db, 'providers'), where(documentId(), 'in', batchIds))).catch(() => null),
          getDocs(query(collection(db, 'provider_verifications'), where('providerId', 'in', batchIds))).catch(() => null),
        ])
        providersSnap?.forEach(d => { providersMap[d.id] = d.data() })
        verifSnap?.forEach(d => {
          const data = d.data() as any
          verificationsMap[data.providerId] = {
            status: data.status,
            submittedAt: data.submittedAt?.toDate?.() ?? data.createdAt?.toDate?.(),
            reviewedAt: data.reviewedAt?.toDate?.(),
            createdAt: data.createdAt?.toDate?.(),
          }
        })
      }

      // Buscar em users os que não estão em providers
      const missingIds = providerIds.filter(id => !providersMap[id])
      for (let i = 0; i < missingIds.length; i += BATCH_SIZE) {
        const batchIds = missingIds.slice(i, i + BATCH_SIZE)
        const usersSnap = await getDocs(query(collection(db, 'users'), where(documentId(), 'in', batchIds))).catch(() => null)
        usersSnap?.forEach(d => { providersMap[d.id] = d.data() })
      }

      // Montar verificações (dados em memória, sem I/O)
      const verificationsData: DocumentVerification[] = allProvidersDocs.map((provider) => {
        const userData = providersMap[provider.providerId]
        const verifData = verificationsMap[provider.providerId]
        const currentStatus = resolveQueueVerificationStatus(
          userData?.verificationStatus,
          verifData?.status
        )

        return {
          id: `verification_${provider.providerId}`,
          providerId: provider.providerId,
          providerName: userData?.fullName || userData?.nome || userData?.displayName || `Prestador ${provider.providerId.slice(-8)}`,
          providerEmail: userData?.email || `prestador${provider.providerId.slice(-8)}@email.com`,
          providerPhone: userData?.phone || userData?.telefone || userData?.phoneNumber || '',
          providerCpf: userData?.cpf || userData?.document || '',
          providerRg: userData?.rg || '',
          providerAddress: typeof userData?.address === 'string' ? userData.address :
            typeof userData?.address === 'object' && userData?.address ?
              `${userData.address.street || ''} ${userData.address.number || ''}, ${userData.address.city || ''}, ${userData.address.state || ''}`.trim().replace(/,$/, '') :
              userData?.endereco || '',
          providerLatitude: extractCoordinates(userData).lat,
          providerLongitude: extractCoordinates(userData).lng,
          providerBirthDate: userData?.birthDate || userData?.dataNascimento || '',
          providerServiceCategories: userData ? extractServiceCategories(userData as Record<string, unknown>) : [],
          status: currentStatus,
          documents: provider.documents,
          submittedAt: verifData?.submittedAt
            ?? verifData?.createdAt
            ?? (userData?.createdAt?.toDate?.() instanceof Date ? userData.createdAt.toDate() : undefined)
            ?? (userData?.updatedAt?.toDate?.() instanceof Date ? userData.updatedAt.toDate() : undefined)
            ?? provider.firstUploadedAt,
          reviewedAt: verifData?.reviewedAt
            ?? (userData?.updatedAt?.toDate?.() instanceof Date && currentStatus !== 'pending' ? userData.updatedAt.toDate() : undefined),
        }
      })

      setVerifications(verificationsData)
      
      // Calcular estatísticas
      const newStats: VerificationStats = {
        total: verificationsData.length,
        pending: verificationsData.filter(v => v.status === 'pending').length,
        approved: verificationsData.filter(v => v.status === 'approved').length,
        rejected: verificationsData.filter(v => v.status === 'rejected').length,
        totalDocuments: 0,
        documentsByType: {}
      }

      // Contar documentos por tipo
      verificationsData.forEach(verification => {
        Object.values(verification.documents).forEach(documents => {
          if (documents) {
            newStats.totalDocuments += documents.length
            documents.forEach(doc => {
              newStats.documentsByType[doc.type] = (newStats.documentsByType[doc.type] || 0) + 1
            })
          }
        })
      })

      setStats(newStats)
    } catch (error: any) {
      toast({
        title: "Erro",
        description: error?.message || "Não foi possível carregar as verificações.",
        variant: "destructive"
      })
    } finally {
      setLoading(false)
    }
  }, [toast])

  // Buscar verificações de um prestador específico
  const fetchProviderVerification = useCallback(async (providerId: string): Promise<DocumentVerification | null> => {
    try {
      const documents = await getProviderDocuments(providerId)
      if (!documents) return null

      // Buscar dados reais do prestador no Firestore (collection 'providers')
      let userData = null
      const providerDoc = await getDoc(doc(db, 'providers', providerId))
      if (providerDoc.exists()) {
        userData = providerDoc.data()
      } else {
        // Tentar buscar na collection 'users' como fallback
        const userDoc = await getDoc(doc(db, 'users', providerId))
        if (userDoc.exists()) {
          userData = userDoc.data()
        }
      }

      return {
        id: `verification_${providerId}`,
        providerId,
        providerName: userData?.fullName || userData?.nome || userData?.displayName || `Prestador ${providerId.slice(-6)}`,
        providerEmail: userData?.email || `prestador${providerId.slice(-6)}@email.com`,
        providerPhone: userData?.phone || userData?.telefone || userData?.phoneNumber || '',
        providerCpf: userData?.cpf || userData?.document || '',
        providerRg: userData?.rg || '',
        providerAddress: typeof userData?.address === 'string' ? userData.address : 
                       typeof userData?.address === 'object' && userData?.address ? 
                       `${userData.address.street || ''} ${userData.address.number || ''}, ${userData.address.city || ''}, ${userData.address.state || ''}`.trim().replace(/,$/, '') :
                       userData?.endereco || '',
        providerLatitude: extractCoordinates(userData).lat,
        providerLongitude: extractCoordinates(userData).lng,
        providerBirthDate: userData?.birthDate || userData?.dataNascimento || '',
        providerServiceCategories: userData ? extractServiceCategories(userData as Record<string, unknown>) : [],
        status: 'pending',
        documents: documents.documents,
        submittedAt: documents.uploadedAt,
      }
    } catch (error) {
      console.error(`Erro ao buscar verificação do prestador ${providerId}:`, error)
      return null
    }
  }, [])

  // Verificar se um prestador tem documentos
  const checkProviderHasDocuments = useCallback(async (providerId: string): Promise<boolean> => {
    try {
      return await hasProviderDocuments(providerId)
    } catch (error) {
      return false
    }
  }, [])

  // Aprovar verificação
  const approveVerification = useCallback(async (verificationId: string, reviewedBy: string) => {
    try {
      const verification = verifications.find(v => v.id === verificationId)
      if (!verification) {
        throw new Error('Verificação não encontrada')
      }

      // Persistir no Firestore
      if (!db) throw new Error('Firestore não inicializado')

      // 1) Atualizar/registrar em provider_verifications (status em UPPERCASE)
      const verificationsRef = collection(db, 'provider_verifications')
      const q = query(verificationsRef, where('providerId', '==', verification.providerId))
      const snap = await getDocs(q)

      if (!snap.empty) {
        // Atualiza o(s) documento(s) existente(s)
        await Promise.all(
          snap.docs.map(d => updateDoc(d.ref, {
            status: 'APPROVED',
            reviewedAt: serverTimestamp(),
            reviewedBy,
          }))
        )
      } else {
        // Cria um novo registro se não existir
        await addDoc(verificationsRef, {
          providerId: verification.providerId,
          status: 'APPROVED',
          createdAt: serverTimestamp(),
          submittedAt: verification.submittedAt || serverTimestamp(),
          reviewedAt: serverTimestamp(),
          reviewedBy,
          id: verificationId,
          notes: '',
        })
      }

      // 2) Atualizar verificationStatus via Admin SDK (regras do Firestore bloqueiam client SDK em providers/)
      const verifyRes = await fetch(`/api/providers/${verification.providerId}/verify`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'approved' }),
      })
      if (!verifyRes.ok) {
        const err = await verifyRes.json().catch(() => ({}))
        throw new Error(err.error || 'Falha ao atualizar status do prestador')
      }

      // Atualizar estado local
      setVerifications(prev => prev.map(v => 
        v.id === verificationId 
          ? { ...v, status: 'approved' as const, reviewedAt: new Date(), reviewedBy }
          : v
      ))

      invalidatePendingProvidersCache()
      toast({
        title: "Verificação aprovada",
        description: `${verification.providerName} foi aprovado como prestador e está habilitado para prestar serviços.`,
      })

      return true
    } catch (error) {
      console.error('Erro ao aprovar verificação:', error)
      toast({
        title: "Erro",
        description: "Não foi possível aprovar a verificação.",
        variant: "destructive"
      })
      return false
    }
  }, [verifications, toast])

  // Rejeitar verificação
  const rejectVerification = useCallback(async (verificationId: string, rejectionReason: string, reviewedBy: string) => {
    try {
      const verification = verifications.find(v => v.id === verificationId)
      if (!verification) {
        throw new Error('Verificação não encontrada')
      }

      // Persistir no Firestore
      if (!db) throw new Error('Firestore não inicializado')

      // 1) Atualizar/registrar em provider_verifications (status em UPPERCASE)
      const verificationsRef = collection(db, 'provider_verifications')
      const q = query(verificationsRef, where('providerId', '==', verification.providerId))
      const snap = await getDocs(q)

      if (!snap.empty) {
        await Promise.all(
          snap.docs.map(d => updateDoc(d.ref, {
            status: 'REJECTED',
            reviewedAt: serverTimestamp(),
            reviewedBy,
            rejectionReason,
          }))
        )
      } else {
        await addDoc(verificationsRef, {
          providerId: verification.providerId,
          status: 'REJECTED',
          createdAt: serverTimestamp(),
          submittedAt: verification.submittedAt || serverTimestamp(),
          reviewedAt: serverTimestamp(),
          reviewedBy,
          id: verificationId,
          notes: '',
          rejectionReason,
        })
      }

      // 2) Atualizar provider via Admin SDK (regras do Firestore bloqueiam client SDK em providers/)
      const verifyRes = await fetch(`/api/providers/${verification.providerId}/verify`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: 'rejected', rejectionReason }),
      })
      if (!verifyRes.ok) {
        const err = await verifyRes.json().catch(() => ({}))
        throw new Error(err.error || 'Falha ao atualizar status do prestador')
      }

      // Atualizar estado local
      setVerifications(prev => prev.map(v => 
        v.id === verificationId 
          ? { ...v, status: 'rejected' as const, rejectionReason, reviewedAt: new Date(), reviewedBy }
          : v
      ))

      invalidatePendingProvidersCache()
      toast({
        title: "Verificação rejeitada",
        description: `${verification.providerName} foi rejeitado como prestador. O motivo foi registrado.`,
        variant: "destructive"
      })

      return true
    } catch (error) {
      toast({
        title: "Erro",
        description: "Não foi possível rejeitar a verificação.",
        variant: "destructive"
      })
      return false
    }
  }, [verifications, toast])

  // Filtrar verificações
  const filterVerifications = useCallback((filters: VerificationFilters) => {
    let filtered = verifications

    if (filters.status && filters.status !== 'all') {
      filtered = filtered.filter(v => v.status === filters.status)
    }

    if (filters.search) {
      const searchLower = filters.search.toLowerCase()
      filtered = filtered.filter(v => 
        v.providerName.toLowerCase().includes(searchLower) ||
        v.providerEmail.toLowerCase().includes(searchLower) ||
        v.providerPhone?.includes(searchLower) ||
        v.providerCpf?.toLowerCase().includes(searchLower) ||
        v.providerRg?.toLowerCase().includes(searchLower)
      )
    }

    if (filters.documentType) {
      filtered = filtered.filter(v => 
        (v.documents[filters.documentType as keyof typeof v.documents]?.length ?? 0) > 0
      )
    }

    return filtered
  }, [verifications])

  // Buscar verificações automaticamente ao carregar
  useEffect(() => {
    fetchVerifications()
  }, [fetchVerifications])

  return {
    verifications,
    loading,
    stats,
    fetchVerifications,
    fetchProviderVerification,
    checkProviderHasDocuments,
    approveVerification,
    rejectVerification,
    filterVerifications,
    refetch: fetchVerifications
  }
}
