import { NextRequest, NextResponse } from 'next/server'
import { adminStorage } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

export const dynamic = 'force-dynamic'

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gestaoUsuarios')
    if (!adminStorage) {
      return NextResponse.json({
        providers: [],
        warning: 'Firebase Admin SDK nao configurado para leitura de documentos.',
      })
    }

    const [files] = await adminStorage.getFiles({ prefix: 'Documentos/' })
    const providersMap = new Map<string, { providerId: string; documents: Record<string, unknown[]>; uploadedAt: Date }>()

    for (const file of files) {
      const pathParts = file.name.split('/')
      if (pathParts.length < 3) {
        continue
      }

      const providerId = pathParts[1]
      if (!providersMap.has(providerId)) {
        providersMap.set(providerId, {
          providerId,
          documents: {},
          uploadedAt: new Date(0),
        })
      }

      const provider = providersMap.get(providerId)!
      const fileName = pathParts[pathParts.length - 1]
      const [signedUrl] = await file.getSignedUrl({
        action: 'read',
        expires: Date.now() + 60 * 60 * 1000,
      })
      const [metadata] = await file.getMetadata()
      const uploadedAt = new Date(metadata.timeCreated || Date.now())

      if (!provider.documents.outros) {
        provider.documents.outros = []
      }

      provider.documents.outros.push({
        id: fileName,
        name: fileName,
        url: signedUrl,
        type: 'image',
        size: Number(metadata.size || 0),
        uploadedAt,
        path: file.name,
      })

      if (uploadedAt > provider.uploadedAt) {
        provider.uploadedAt = uploadedAt
      }
    }

    return NextResponse.json({
      providers: Array.from(providersMap.values()),
      warning: providersMap.size === 0 ? 'Nenhum documento real encontrado no Storage.' : undefined,
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro no Firebase Admin SDK:', error)
    return NextResponse.json({
      providers: [],
      warning: 'Nao foi possivel carregar documentos reais do Firebase Storage.',
    })
  }
}
