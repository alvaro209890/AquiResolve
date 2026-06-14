/**
 * SERVIÇO DE INTEGRAÇÃO PAGAR.ME
 * API v5 - Documentação: https://docs.pagar.me/
 */

import {
  PagarmeCustomer,
  PagarmeOrder,
  PagarmeCharge,
  PagarmeSubscription,
  PagarmeCard,
  PagarmePlan,
  PagarmeRecipient,
  PagarmeBalance,
  PagarmeTransfer,
  PagarmeApiResponse,
  CreatePagarmeOrderRequest,
  CreatePagarmeSubscriptionRequest,
  PagarmeListQuery,
  PagarmeAnalytics,
} from '@/types/pagarme'

// Configuração da API
const PAGARME_API_URL = 'https://api.pagar.me/core/v5'

type PagarmeRawResponse<T> = {
  data?: T
  paging?: PagarmeApiResponse<T>['paging']
}

function buildBasicAuthHeader(apiKey: string): string {
  const credentials = `${apiKey}:`
  const encoded =
    typeof Buffer !== 'undefined'
      ? Buffer.from(credentials).toString('base64')
      : btoa(credentials)

  return `Basic ${encoded}`
}

// Classe de serviço do Pagar.me
export class PagarmeService {
  private apiKey: string
  private isProduction: boolean

  constructor(apiKey?: string) {
    // Usar API Key do ambiente ou passar manualmente
    this.apiKey = apiKey || process.env.API_KEY_PRIVATE_PAGARME || ''
    this.isProduction = process.env.NODE_ENV === 'production'

    if (!this.apiKey && typeof window === 'undefined') {
      console.warn('API_KEY_PRIVATE_PAGARME não configurada')
    }
  }

  /**
   * Faz requisição à API do Pagar.me
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<PagarmeApiResponse<T>> {
    try {
      if (!this.apiKey) {
        return {
          errors: [{
            type: 'config_error',
            message: 'API_KEY_PRIVATE_PAGARME não configurada'
          }]
        }
      }

      const url = `${PAGARME_API_URL}${endpoint}`

      const headers = {
        'Content-Type': 'application/json',
        'Authorization': buildBasicAuthHeader(this.apiKey),
        ...options.headers,
      }

      const response = await fetch(url, {
        ...options,
        headers,
      })

      const responseText = await response.text()
      const data = responseText ? JSON.parse(responseText) : null

      if (!response.ok) {
        console.error('Erro Pagar.me:', data)
        return {
          errors: data?.errors || [{
            type: 'api_error',
            message: data?.message || `Erro ao processar requisição (${response.status})`
          }]
        }
      }

      const rawData = data as PagarmeRawResponse<T>
      return {
        data: rawData && Object.prototype.hasOwnProperty.call(rawData, 'data')
          ? rawData.data
          : (data as T),
        paging: rawData?.paging,
      }
    } catch (error) {
      console.error('Erro na requisição Pagar.me:', error)
      return {
        errors: [{
          type: 'network_error',
          message: error instanceof Error ? error.message : 'Erro de conexão'
        }]
      }
    }
  }
  // ==========================================
  // CLIENTES
  // ==========================================

  /**
   * Lista todos os clientes
   */
  async listCustomers(query?: PagarmeListQuery): Promise<PagarmeApiResponse<PagarmeCustomer[]>> {
    const params = new URLSearchParams()
    if (query?.page) params.append('page', query.page.toString())
    if (query?.size) params.append('size', query.size.toString())
    if (query?.code) params.append('code', query.code)
    if (query?.created_since) params.append('created_since', query.created_since)
    if (query?.created_until) params.append('created_until', query.created_until)

    return this.request<PagarmeCustomer[]>(`/customers?${params.toString()}`)
  }

  /**
   * Busca um cliente por ID
   */
  async getCustomer(customerId: string): Promise<PagarmeApiResponse<PagarmeCustomer>> {
    return this.request<PagarmeCustomer>(`/customers/${customerId}`)
  }

  /**
   * Cria um novo cliente
   */
  async createCustomer(customer: Partial<PagarmeCustomer>): Promise<PagarmeApiResponse<PagarmeCustomer>> {
    return this.request<PagarmeCustomer>('/customers', {
      method: 'POST',
      body: JSON.stringify(customer),
    })
  }

  /**
   * Atualiza um cliente
   */
  async updateCustomer(
    customerId: string,
    customer: Partial<PagarmeCustomer>
  ): Promise<PagarmeApiResponse<PagarmeCustomer>> {
    return this.request<PagarmeCustomer>(`/customers/${customerId}`, {
      method: 'PUT',
      body: JSON.stringify(customer),
    })
  }

  // ==========================================
  // PEDIDOS / TRANSAÃ‡Ã•ES
  // ==========================================

  /**
   * Lista todos os pedidos
   */
  async listOrders(query?: PagarmeListQuery): Promise<PagarmeApiResponse<PagarmeOrder[]>> {
    const params = new URLSearchParams()
    if (query?.page) params.append('page', query.page.toString())
    if (query?.size) params.append('size', query.size.toString())
    if (query?.code) params.append('code', query.code)
    if (query?.status) params.append('status', query.status)
    if (query?.customer_id) params.append('customer_id', query.customer_id)
    if (query?.created_since) params.append('created_since', query.created_since)
    if (query?.created_until) params.append('created_until', query.created_until)

    return this.request<PagarmeOrder[]>(`/orders?${params.toString()}`)
  }

  /**
   * Busca um pedido por ID
   */
  async getOrder(orderId: string): Promise<PagarmeApiResponse<PagarmeOrder>> {
    return this.request<PagarmeOrder>(`/orders/${orderId}`)
  }

  /**
   * Cria um novo pedido
   */
  async createOrder(order: CreatePagarmeOrderRequest): Promise<PagarmeApiResponse<PagarmeOrder>> {
    return this.request<PagarmeOrder>('/orders', {
      method: 'POST',
      body: JSON.stringify(order),
    })
  }

  /**
   * Cancela um pedido
   */
  async cancelOrder(orderId: string): Promise<PagarmeApiResponse<PagarmeOrder>> {
    return this.request<PagarmeOrder>(`/orders/${orderId}`, {
      method: 'DELETE',
    })
  }

  // ==========================================
  // COBRANÃ‡AS
  // ==========================================

  /**
   * Lista todas as cobranÃ§as
   */
  async listCharges(query?: PagarmeListQuery): Promise<PagarmeApiResponse<PagarmeCharge[]>> {
    const params = new URLSearchParams()
    if (query?.page) params.append('page', query.page.toString())
    if (query?.size) params.append('size', query.size.toString())
    if (query?.status) params.append('status', query.status)
    if (query?.customer_id) params.append('customer_id', query.customer_id)
    if (query?.created_since) params.append('created_since', query.created_since)
    if (query?.created_until) params.append('created_until', query.created_until)

    return this.request<PagarmeCharge[]>(`/charges?${params.toString()}`)
  }

  /**
   * Busca uma cobranÃ§a por ID
   */
  async getCharge(chargeId: string): Promise<PagarmeApiResponse<PagarmeCharge>> {
    return this.request<PagarmeCharge>(`/charges/${chargeId}`)
  }

  /**
   * Captura uma cobranÃ§a autorizada
   */
  async captureCharge(chargeId: string, amount?: number): Promise<PagarmeApiResponse<PagarmeCharge>> {
    return this.request<PagarmeCharge>(`/charges/${chargeId}/capture`, {
      method: 'POST',
      body: JSON.stringify(amount ? { amount } : {}),
    })
  }

  /**
   * Reembolsa uma cobranÃ§a
   */
  async refundCharge(chargeId: string, amount?: number): Promise<PagarmeApiResponse<PagarmeCharge>> {
    return this.request<PagarmeCharge>(`/charges/${chargeId}/refund`, {
      method: 'POST',
      body: JSON.stringify(amount ? { amount } : {}),
    })
  }

  /**
   * Cancela uma cobranÃ§a
   */
  async cancelCharge(chargeId: string): Promise<PagarmeApiResponse<PagarmeCharge>> {
    return this.request<PagarmeCharge>(`/charges/${chargeId}`, {
      method: 'DELETE',
    })
  }

  // ==========================================
  // ASSINATURAS
  // ==========================================

  /**
   * Lista todas as assinaturas
   */
  async listSubscriptions(query?: PagarmeListQuery): Promise<PagarmeApiResponse<PagarmeSubscription[]>> {
    const params = new URLSearchParams()
    if (query?.page) params.append('page', query.page.toString())
    if (query?.size) params.append('size', query.size.toString())
    if (query?.code) params.append('code', query.code)
    if (query?.status) params.append('status', query.status)
    if (query?.customer_id) params.append('customer_id', query.customer_id)
    if (query?.created_since) params.append('created_since', query.created_since)
    if (query?.created_until) params.append('created_until', query.created_until)

    return this.request<PagarmeSubscription[]>(`/subscriptions?${params.toString()}`)
  }

  /**
   * Busca uma assinatura por ID
   */
  async getSubscription(subscriptionId: string): Promise<PagarmeApiResponse<PagarmeSubscription>> {
    return this.request<PagarmeSubscription>(`/subscriptions/${subscriptionId}`)
  }

  /**
   * Cria uma nova assinatura
   */
  async createSubscription(
    subscription: CreatePagarmeSubscriptionRequest
  ): Promise<PagarmeApiResponse<PagarmeSubscription>> {
    return this.request<PagarmeSubscription>('/subscriptions', {
      method: 'POST',
      body: JSON.stringify(subscription),
    })
  }

  /**
   * Atualiza uma assinatura
   */
  async updateSubscription(
    subscriptionId: string,
    subscription: Partial<PagarmeSubscription>
  ): Promise<PagarmeApiResponse<PagarmeSubscription>> {
    return this.request<PagarmeSubscription>(`/subscriptions/${subscriptionId}`, {
      method: 'PUT',
      body: JSON.stringify(subscription),
    })
  }

  /**
   * Cancela uma assinatura
   */
  async cancelSubscription(subscriptionId: string): Promise<PagarmeApiResponse<PagarmeSubscription>> {
    return this.request<PagarmeSubscription>(`/subscriptions/${subscriptionId}`, {
      method: 'DELETE',
    })
  }

  // ==========================================
  // CARTÃ•ES
  // ==========================================

  /**
   * Lista cartÃµes de um cliente
   */
  async listCards(customerId: string): Promise<PagarmeApiResponse<PagarmeCard[]>> {
    return this.request<PagarmeCard[]>(`/customers/${customerId}/cards`)
  }

  /**
   * Busca um cartÃ£o por ID
   */
  async getCard(customerId: string, cardId: string): Promise<PagarmeApiResponse<PagarmeCard>> {
    return this.request<PagarmeCard>(`/customers/${customerId}/cards/${cardId}`)
  }

  /**
   * Remove um cartÃ£o
   */
  async deleteCard(customerId: string, cardId: string): Promise<PagarmeApiResponse<PagarmeCard>> {
    return this.request<PagarmeCard>(`/customers/${customerId}/cards/${cardId}`, {
      method: 'DELETE',
    })
  }

  // ==========================================
  // PLANOS
  // ==========================================

  /**
   * Lista todos os planos
   */
  async listPlans(query?: PagarmeListQuery): Promise<PagarmeApiResponse<PagarmePlan[]>> {
    const params = new URLSearchParams()
    if (query?.page) params.append('page', query.page.toString())
    if (query?.size) params.append('size', query.size.toString())
    if (query?.status) params.append('status', query.status)

    return this.request<PagarmePlan[]>(`/plans?${params.toString()}`)
  }

  /**
   * Busca um plano por ID
   */
  async getPlan(planId: string): Promise<PagarmeApiResponse<PagarmePlan>> {
    return this.request<PagarmePlan>(`/plans/${planId}`)
  }

  /**
   * Cria um novo plano
   */
  async createPlan(plan: Partial<PagarmePlan>): Promise<PagarmeApiResponse<PagarmePlan>> {
    return this.request<PagarmePlan>('/plans', {
      method: 'POST',
      body: JSON.stringify(plan),
    })
  }

  /**
   * Atualiza um plano
   */
  async updatePlan(planId: string, plan: Partial<PagarmePlan>): Promise<PagarmeApiResponse<PagarmePlan>> {
    return this.request<PagarmePlan>(`/plans/${planId}`, {
      method: 'PUT',
      body: JSON.stringify(plan),
    })
  }

  /**
   * Deleta um plano
   */
  async deletePlan(planId: string): Promise<PagarmeApiResponse<PagarmePlan>> {
    return this.request<PagarmePlan>(`/plans/${planId}`, {
      method: 'DELETE',
    })
  }

  // ==========================================
  // SALDO E TRANSFERÃŠNCIAS
  // ==========================================

  /**
   * Busca saldo da conta
   */
  async getBalance(): Promise<PagarmeApiResponse<PagarmeBalance>> {
    const recipientsResponse = await this.request<PagarmeRecipient[]>('/recipients?size=1')
    const recipientId = recipientsResponse.data?.[0]?.id

    if (recipientsResponse.errors || !recipientId) {
      return {
        errors: recipientsResponse.errors || [{
          type: 'api_error',
          message: 'Nenhum recebedor Pagar.me encontrado para consultar saldo'
        }]
      }
    }

    return this.request<PagarmeBalance>(`/recipients/${encodeURIComponent(recipientId)}/balance`)
  }

  /**
   * Lista transferÃªncias
   */
  async listTransfers(query?: PagarmeListQuery): Promise<PagarmeApiResponse<PagarmeTransfer[]>> {
    const params = new URLSearchParams()
    if (query?.page) params.append('page', query.page.toString())
    if (query?.size) params.append('size', query.size.toString())
    if (query?.status) params.append('status', query.status)

    return this.request<PagarmeTransfer[]>(`/transfers?${params.toString()}`)
  }

  // ==========================================
  // ANALYTICS E RELATÃ“RIOS
  // ==========================================

  /**
   * Gera analytics financeiras
   */
  async getAnalytics(
    startDate: string,
    endDate: string
  ): Promise<PagarmeAnalytics> {
    try {
      // Buscar pedidos do perÃ­odo
      const ordersResponse = await this.listOrders({
        created_since: startDate,
        created_until: endDate,
        size: 1000,
      })

      const orders = ordersResponse.data || []

      // Buscar assinaturas ativas
      const subscriptionsResponse = await this.listSubscriptions({
        status: 'active',
        size: 1000,
      })

      const subscriptions = subscriptionsResponse.data || []

      // Buscar clientes Ãºnicos
      const uniqueCustomers = new Set(orders.map(o => o.customer.id))

      // Calcular totais
      const totalAmount = orders
        .filter(o => o.status === 'paid')
        .reduce((sum, o) => sum + o.amount, 0)

      // Contar por mÃ©todo de pagamento
      const paymentMethods = {
        credit_card: 0,
        debit_card: 0,
        pix: 0,
        boleto: 0,
      }

      orders.forEach(order => {
        order.charges?.forEach(charge => {
          if (charge.payment_method in paymentMethods) {
            paymentMethods[charge.payment_method as keyof typeof paymentMethods]++
          }
        })
      })

      // Contar por status
      const statusBreakdown = {
        paid: orders.filter(o => o.status === 'paid').length,
        pending: orders.filter(o => o.status === 'pending').length,
        failed: orders.filter(o => o.status === 'failed').length,
        canceled: orders.filter(o => o.status === 'canceled').length,
      }

      return {
        total_amount: totalAmount,
        total_orders: orders.length,
        total_customers: uniqueCustomers.size,
        total_subscriptions: subscriptions.length,
        payment_methods: paymentMethods,
        status_breakdown: statusBreakdown,
        period: {
          start: startDate,
          end: endDate,
        },
      }
    } catch (error) {
      console.error('Erro ao gerar analytics:', error)
      return {
        total_amount: 0,
        total_orders: 0,
        total_customers: 0,
        total_subscriptions: 0,
        payment_methods: {
          credit_card: 0,
          debit_card: 0,
          pix: 0,
          boleto: 0,
        },
        status_breakdown: {
          paid: 0,
          pending: 0,
          failed: 0,
          canceled: 0,
        },
        period: {
          start: startDate,
          end: endDate,
        },
      }
    }
  }

  // ==========================================
  // HELPERS
  // ==========================================

  /**
   * Formata valor em centavos
   */
  static toCents(value: number): number {
    return Math.round(value * 100)
  }

  /**
   * Formata valor de centavos para reais
   */
  static fromCents(value: number): number {
    return value / 100
  }

  /**
   * Valida CPF/CNPJ
   */
  static validateDocument(document: string): boolean {
    const cleanDoc = document.replace(/\D/g, '')
    return cleanDoc.length === 11 || cleanDoc.length === 14
  }

  /**
   * Formata moeda BRL
   */
  static formatCurrency(value: number): string {
    return new Intl.NumberFormat('pt-BR', {
      style: 'currency',
      currency: 'BRL',
    }).format(value)
  }
}

// InstÃ¢ncia singleton do serviÃ§o
export const pagarmeService = new PagarmeService()
