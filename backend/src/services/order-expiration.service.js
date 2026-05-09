/**
 * Serviço de Expiração de Pedidos
 * 
 * Verifica pedidos que estão em "distributing" ou "pending" há mais de 1h30
 * e os marca como "expired".
 * 
 * Uso: Cloud Function programada (cron) ou endpoint via cron-job.org
 * 
 * Firestore estrutura de notificações já usada pelo app:
 *   userTokens/{userId}: { fcmToken, deviceType, lastUpdated }
 *   notifications/: { userId, title, message, type, orderId, isRead, timestamp }
 */

const admin = require('firebase-admin');
const { initializeFirebase } = require('../config/firebase');
const logger = require('../utils/logger');

const EXPIRATION_TIMEOUT_MS = 90 * 60 * 1000; // 1h30 em ms
const ORDER_STATUSES_TO_CHECK = ['distributing', 'pending', 'DISTRIBUTING', 'PENDING'];

/**
 * Executa a verificação e expiração de pedidos.
 * Deve ser chamada por cron a cada ~10-15 minutos.
 */
async function expireOrders() {
  initializeFirebase();
  const db = admin.firestore();
  const now = Date.now();
  const cutoff = new Date(now - EXPIRATION_TIMEOUT_MS);

  logger.info('Iniciando verificação de expiração de pedidos', {
    cutoff: cutoff.toISOString(),
    timeoutMs: EXPIRATION_TIMEOUT_MS
  });

  try {
    // Buscar pedidos em distributing/pending
    const snapshot = await db.collection('orders')
      .where('status', 'in', ORDER_STATUSES_TO_CHECK)
      .get();

    if (snapshot.empty) {
      logger.info('Nenhum pedido pendente para verificar');
      return { checked: 0, expired: 0, errors: 0 };
    }

    let expiredCount = 0;
    let errorCount = 0;

    const expirationBatch = db.batch();

    for (const doc of snapshot.docs) {
      const data = doc.data();
      const createdAt = data.createdAt?.toDate();
      const distributionStartedAt = data.distributionStartedAt?.toDate();

      // Usar o distributionStartedAt se existir, senão createdAt
      const referenceTime = distributionStartedAt || createdAt;

      if (!referenceTime) {
        logger.warn('Pedido sem data de referência', { orderId: doc.id });
        continue;
      }

      // Verificar se já passou do timeout
      if (referenceTime.getTime() > cutoff.getTime()) {
        continue; // Ainda dentro do prazo
      }

      logger.info('Expiramdo pedido', {
        orderId: doc.id,
        clientId: data.clientId,
        createdAt: createdAt?.toISOString(),
        distributionStartedAt: distributionStartedAt?.toISOString(),
        ageMinutes: Math.round((now - referenceTime.getTime()) / 60000)
      });

      try {
        // 1. Atualizar status para expired no batch
        expirationBatch.update(doc.ref, {
          status: 'expired',
          expiredAt: admin.firestore.Timestamp.now(),
          updatedAt: admin.firestore.Timestamp.now()
        });

        // 2. Salvar notificação no Firestore (coleção "notifications")
        const notificationRef = db.collection('notifications').doc();
        expirationBatch.set(notificationRef, {
          userId: data.clientId,
          title: 'Pedido não encontrou prestador',
          message: `Seu pedido #${data.protocol || doc.id.slice(0, 8)} não foi aceito por nenhum prestador dentro do prazo.`,
          type: 'order_update',
          orderId: doc.id,
          isRead: false,
          timestamp: admin.firestore.Timestamp.now(),
          data: {
            status: 'expired',
            protocol: data.protocol || ''
          }
        });

        // 3. Enviar notificação push via FCM
        await sendPushNotification(db, data.clientId, {
          title: 'Pedido não encontrou prestador',
          message: `Seu pedido #${data.protocol || doc.id.slice(0, 8)} não foi aceito por nenhum prestador.`,
          type: 'order',
          orderId: doc.id
        });

        expiredCount++;
      } catch (err) {
        logger.error('Erro ao expirar pedido individual', {
          orderId: doc.id,
          error: err.message
        });
        errorCount++;
      }
    }

    // Commitar batch de atualizações
    if (expiredCount > 0) {
      await expirationBatch.commit();
      logger.info(`Batch commit realizado: ${expiredCount} pedidos expirados`);
    }

    const result = {
      checked: snapshot.size,
      expired: expiredCount,
      errors: errorCount
    };

    logger.info('Verificação de expiração concluída', result);
    return result;

  } catch (err) {
    logger.error('Erro geral na verificação de expiração', {
      error: err.message,
      stack: err.stack
    });
    throw err;
  }
}

/**
 * Envia notificação push FCM para um usuário
 */
async function sendPushNotification(db, userId, data) {
  try {
    // Buscar token FCM do usuário
    const tokenDoc = await db.collection('userTokens').doc(userId).get();

    if (!tokenDoc.exists) {
      logger.warn('Token FCM não encontrado para usuário', { userId });
      return;
    }

    const fcmToken = tokenDoc.data().fcmToken;
    if (!fcmToken || fcmToken.startsWith('disabled_token_')) {
      logger.warn('Token FCM inválido para usuário', { userId });
      return;
    }

    // Enviar via Firebase Cloud Messaging
    const message = {
      token: fcmToken,
      notification: {
        title: data.title,
        body: data.message
      },
      data: {
        type: data.type,
        orderId: data.orderId,
        title: data.title,
        message: data.message
      },
      android: {
        priority: 'high',
        notification: {
          channelId: 'default_channel',
          priority: 'high',
          sound: 'default'
        }
      },
      apns: {
        payload: {
          aps: {
            sound: 'default',
            badge: 1
          }
        }
      }
    };

    const response = await admin.messaging().send(message);
    logger.info('Push enviado com sucesso', {
      userId,
      orderId: data.orderId,
      messageId: response
    });
  } catch (err) {
    // Erros comuns: token expirado, device offline
    if (err.code === 'messaging/registration-token-not-registered') {
      logger.warn('Token FCM expirado para usuário', { userId });
    } else {
      logger.error('Erro ao enviar push FCM', {
        userId,
        error: err.code || err.message
      });
    }
  }
}

module.exports = {
  expireOrders,
  EXPIRATION_TIMEOUT_MS
};
