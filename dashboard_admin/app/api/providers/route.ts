import { NextRequest, NextResponse } from 'next/server'
import { adminStorage } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

export const dynamic = 'force-dynamic'

export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gestaoUsuarios')
    // Buscar prestadores via Firebase Admin SDK
    if (adminStorage) {
      
      const [files] = await adminStorage.getFiles({ prefix: 'Documentos/' })
      const dirMap = new Map<string, any[]>()
      
      for (const file of files) {
        const name = file.name
        const parts = name.split('/')
        if (parts.length < 3) continue
        const providerId = parts[1]
        if (!dirMap.has(providerId)) dirMap.set(providerId, [])
        dirMap.get(providerId)!.push(file)
      }

      const providers: any[] = []
      for (const [providerId, providerFiles] of dirMap.entries()) {
        const documents: Record<string, any[]> = {}
        let uploadedAt = 0
        
        for (const file of providerFiles) {
          const [metadata] = await file.getMetadata()
          const [signedUrl] = await file.getSignedUrl({ 
            action: 'read', 
            expires: Date.now() + 60 * 60 * 1000 
          })
          const fileName = file.name.split('/').pop() || ''
          const ext = (fileName.split('.').pop() || '').toLowerCase()
          const type = ['jpg','jpeg','png','gif','webp','bmp'].includes(ext) ? 'image' : ext === 'pdf' ? 'pdf' : 'document'
          const docType = 'outros'
          
          const item = {
            id: fileName,
            name: fileName,
            url: signedUrl,
            type,
            size: Number(metadata.size || 0),
            uploadedAt: new Date(metadata.timeCreated || Date.now()),
            path: file.name,
          }
          
          if (!documents[docType]) documents[docType] = []
          documents[docType].push(item)
          const ts = new Date(item.uploadedAt).getTime()
          if (ts > uploadedAt) uploadedAt = ts
        }
        
        providers.push({ 
          providerId, 
          documents, 
          uploadedAt: new Date(uploadedAt || Date.now()) 
        })
      }

      return NextResponse.json({ providers })
    }
    
    // Firebase Admin SDK não disponível
    return NextResponse.json({ 
      providers: [],
      message: "Firebase Admin SDK não configurado" 
    })
    
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('Erro ao buscar prestadores:', error)
    return NextResponse.json({ 
      providers: [],
      message: "Erro ao carregar prestadores"
    }, { status: 200 })
  }
}
