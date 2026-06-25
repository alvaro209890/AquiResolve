import { NextRequest, NextResponse } from 'next/server'
import { getAdminFirestore } from '@/lib/firebase-admin'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

function toIso(value: unknown): string | null {
  if (!value) return null
  if (value instanceof Date) return value.toISOString()
  if (typeof (value as { toDate?: () => Date }).toDate === 'function') {
    return (value as { toDate: () => Date }).toDate().toISOString()
  }
  if (typeof value === 'string') return value
  return null
}

function readString(data: Record<string, unknown>, key: string): string {
  const value = data[key]
  return typeof value === 'string' ? value : ''
}

function readStringArray(data: Record<string, unknown>, key: string): string[] {
  const value = data[key]
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

function checklistStatusLabel(status: string): string {
  switch (status) {
    case 'checklist_pending':
      return 'Checklist pendente'
    case 'photos_pending':
      return 'Fotos pendentes'
    case 'ready_for_completion_code':
      return 'Aguardando código do cliente'
    case 'completed':
      return 'Checklist completo'
    case 'signatures_pending':
      return 'Legado: assinatura pendente'
    default:
      return status || 'Não iniciado'
  }
}

// GET /api/checklists — lista checklists mobile + contexto do pedido para a aba administrativa.
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'gestaoPedidos')
    const db = getAdminFirestore()
    const limitParam = Number(request.nextUrl.searchParams.get('limit') ?? '200')
    const maxRows = Number.isFinite(limitParam) ? Math.min(Math.max(limitParam, 1), 500) : 200

    const checklistSnap = await db.collection('checklists').limit(maxRows).get()
    const rows = await Promise.all(
      checklistSnap.docs.map(async (doc) => {
        const checklist = doc.data() as Record<string, unknown>
        const orderId = readString(checklist, 'orderId') || doc.id
        const orderSnap = await db.collection('orders').doc(orderId).get()
        const order = orderSnap.exists ? (orderSnap.data() as Record<string, unknown>) : {}

        const providerId = readString(checklist, 'providerId') || readString(order, 'assignedProviderId')
        const clientId = readString(order, 'clientId') || readString(order, 'userId') || readString(order, 'clientUid')
        const status = readString(checklist, 'status')
        const photosBefore = readStringArray(checklist, 'photosBefore')
        const photosDuring = readStringArray(checklist, 'photosDuring')
        const photosAfter = readStringArray(checklist, 'photosAfter')

        return {
          id: doc.id,
          orderId,
          protocol: readString(order, 'protocol') || orderId,
          orderStatus: readString(order, 'status'),
          checklistStatus: status,
          checklistStatusLabel: checklistStatusLabel(status),
          serviceName: readString(order, 'serviceName') || readString(order, 'serviceType'),
          serviceType: readString(order, 'serviceType'),
          clientId,
          clientName: readString(order, 'clientName') || readString(checklist, 'clientName'),
          providerId,
          providerName:
            readString(order, 'assignedProviderName') ||
            readString(checklist, 'providerName') ||
            readString(checklist, 'providerSignatureName'),
          startedAt: toIso(checklist.startedAt),
          completedAt: toIso(checklist.completedAt),
          updatedAt: toIso(checklist.updatedAt),
          materialsUsed: checklist.materialsUsed === true,
          materialsDescription: readString(checklist, 'materialsDescription'),
          photosBeforeCount: photosBefore.length,
          photosDuringCount: photosDuring.length,
          photosAfterCount: photosAfter.length,
          totalPhotos: photosBefore.length + photosDuring.length + photosAfter.length,
          problemResolution: readString(checklist, 'problemResolution'),
        }
      })
    )

    rows.sort((a, b) => {
      const da = Date.parse(a.updatedAt || a.startedAt || '') || 0
      const dbb = Date.parse(b.updatedAt || b.startedAt || '') || 0
      return dbb - da
    })

    return NextResponse.json({ success: true, checklists: rows })
  } catch (error: unknown) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    const message = error instanceof Error ? error.message : String(error)
    return NextResponse.json({ success: false, error: message }, { status: 500 })
  }
}
