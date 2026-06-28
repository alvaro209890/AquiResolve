const admin = require('firebase-admin');
const logger = require('../utils/logger');

/**
 * Serviço de notificação de novos pedidos para prestadores.
 *
 * Escuta mudanças em `orders` com status distributing/pending e envia FCM
 * para prestadores aprovados cujos nichos batem com o pedido.
 *
 * Inicializado no app.js (sem bloquear o startup).
 */
class ProviderNotificationService {
  constructor() {
    this.db = admin.firestore();
    this.listener = null;
    this.started = false;
  }

  start() {
    if (this.started) return;
    this.started = true;

    const statuses = ['pending', 'distributing', 'available',
      'PENDING', 'DISTRIBUTING', 'AVAILABLE'];

    this.listener = this.db.collection('orders')
      .where('status', 'in', statuses)
      .onSnapshot(async (snapshot) => {
        for (const change of snapshot.docChanges()) {
          if (change.type === 'added') {
            const order = { id: change.doc.id, ...change.doc.data() };
            await this.notifyProviders(order).catch(err =>
              logger.error('Erro ao notificar prestadores', {
                orderId: order.id,
                error: err.message
              })
            );
          }
        }
      }, (err) => {
        logger.error('Listener de notificações de pedidos caiu', { error: err.message });
        this.started = false;
        // Tenta reiniciar após 10s
        setTimeout(() => this.start(), 10000);
      });

    logger.info('Serviço de notificação de pedidos para prestadores iniciado');
  }

  stop() {
    if (this.listener) {
      this.listener();
      this.listener = null;
    }
    this.started = false;
  }

  /**
   * Notifica prestadores aprovados cujos nichos batem com o pedido.
   */
  async notifyProviders(order) {
    const niche = (order.serviceCategory || order.service_category_name || '').trim();
    if (!niche) {
      logger.warn('Pedido sem nicho, ignorando notificação', { orderId: order.id });
      return;
    }

    // Busca prestadores com o nicho correspondente
    const providersSnap = await this.db.collection('providers')
      .where('services', 'array-contains', niche)
      .get();

    if (providersSnap.empty) {
      logger.info('Nenhum prestador encontrado para o nicho', { niche, orderId: order.id });
      return;
    }

    const tokens = [];
    for (const doc of providersSnap.docs) {
      const provider = doc.data();

      // Verifica se está aprovado
      const status = (provider.verificationStatus || '').toLowerCase();
      const isApproved = ['approved', 'verified', 'aprovado', 'verificado'].includes(status)
        || provider.isVerified === true || provider.verified === true;
      if (!isApproved) continue;

      // Verifica se está disponível
      if (provider.isAvailable === false) continue;

      // Busca token FCM
      const tokenDoc = await this.db.collection('fcm_tokens').doc(doc.id).get();
      const token = tokenDoc.exists ? tokenDoc.data().token : null;
      if (token) tokens.push(token);
    }

    if (tokens.length === 0) {
      logger.info('Nenhum token FCM disponível para prestadores do nicho', { niche });
      return;
    }

    // Remove duplicatas
    const uniqueTokens = [...new Set(tokens)];

    // Envia FCM
    const message = {
      notification: {
        title: 'Novo pedido disponível',
        body: `Pedido de ${niche} aguardando prestador!`,
      },
      data: {
        type: 'order',
        order_id: order.id,
        niche: niche,
      },
      android: {
        priority: 'high',
        notification: {
          sound: 'default',
          channelId: 'orders',
          priority: 'high',
        },
      },
    };

    try {
      const response = await admin.messaging().sendEachForMulticast({
        tokens: uniqueTokens,
        ...message,
      });
      logger.info('FCM enviado para prestadores', {
        niche,
        orderId: order.id,
        success: response.successCount,
        failure: response.failureCount,
        tokens: uniqueTokens.length,
      });
    } catch (err) {
      logger.error('Erro ao enviar FCM multicast', { error: err.message, niche });
    }
  }
}

module.exports = new ProviderNotificationService();
