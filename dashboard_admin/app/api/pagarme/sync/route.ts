import { NextRequest, NextResponse } from 'next/server'
import { pagarmeService } from '@/lib/services/pagarme-service'
import { PagarmeFirebaseSync } from '@/lib/services/pagarme-firebase-sync'
import { adminAuthorizationResponse, requireAdminPermission } from '@/lib/server/admin-authorization'

/**
 * POST /api/pagarme/sync
 * Sincroniza todos os dados do Pagar.me com o Firebase
 */
export async function POST(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'operarFinanceiro')
    const body = await request.json()
    const { type = 'all', limit = 100 } = body

    let syncedOrders = 0
    let syncedCharges = 0
    let syncedCustomers = 0

    // Sincronizar pedidos
    if (type === 'all' || type === 'orders') {
      const ordersResponse = await pagarmeService.listOrders({ size: limit })
      if (ordersResponse.data) {
        await PagarmeFirebaseSync.syncOrders(ordersResponse.data)
        syncedOrders = ordersResponse.data.length
      }
    }

    // Sincronizar cobranças
    if (type === 'all' || type === 'charges') {
      const chargesResponse = await pagarmeService.listCharges({ size: limit })
      if (chargesResponse.data) {
        await PagarmeFirebaseSync.syncCharges(chargesResponse.data)
        syncedCharges = chargesResponse.data.length
      }
    }

    // Sincronizar clientes
    if (type === 'all' || type === 'customers') {
      const customersResponse = await pagarmeService.listCustomers({ size: limit })
      if (customersResponse.data) {
        for (const customer of customersResponse.data) {
          await PagarmeFirebaseSync.saveCustomer(customer)
        }
        syncedCustomers = customersResponse.data.length
      }
    }

    // Registrar log de sincronização
    await PagarmeFirebaseSync.logSync('manual_sync', 
      syncedOrders + syncedCharges + syncedCustomers, 
      'success', 
      { orders: syncedOrders, charges: syncedCharges, customers: syncedCustomers }
    )

    return NextResponse.json({
      success: true,
      message: 'Sincronização concluída com sucesso',
      synced: {
        orders: syncedOrders,
        charges: syncedCharges,
        customers: syncedCustomers,
        total: syncedOrders + syncedCharges + syncedCustomers
      }
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('❌ Erro na sincronização:', error)
    
    // Registrar log de erro
    try {
      await PagarmeFirebaseSync.logSync('manual_sync', 0, 'error', { error: String(error) })
    } catch {}

    return NextResponse.json(
      {
        success: false,
        error: 'Erro na sincronização',
      },
      { status: 500 }
    )
  }
}

/**
 * GET /api/pagarme/sync
 * Retorna status da última sincronização
 */
export async function GET(request: NextRequest) {
  try {
    await requireAdminPermission(request, 'financeiro')
    // Buscar última sincronização do Firebase
    const orders = await PagarmeFirebaseSync.getOrders({ limit: 1 })
    const charges = await PagarmeFirebaseSync.getCharges({ limit: 1 })

    return NextResponse.json({
      success: true,
      data: {
        last_order_sync: orders[0]?.synced_at || null,
        last_charge_sync: charges[0]?.synced_at || null,
        total_orders: orders.length,
        total_charges: charges.length,
      }
    })
  } catch (error) {
    const denied = adminAuthorizationResponse(error)
    if (denied) return denied
    console.error('❌ Erro ao buscar status:', error)
    return NextResponse.json(
      {
        success: false,
        error: 'Erro ao buscar status da sincronização',
      },
      { status: 500 }
    )
  }
}
