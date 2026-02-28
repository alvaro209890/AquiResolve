const { verifyFirebaseToken } = require('../config/firebase');
const HttpError = require('../utils/http-error');
const logger = require('../utils/logger');

async function authenticateRequest(req, _res, next) {
  try {
    const authorization = req.headers.authorization || '';

    if (!authorization.startsWith('Bearer ')) {
      throw new HttpError(401, 'Header Authorization ausente ou inválido', {
        code: 'UNAUTHORIZED'
      });
    }

    const idToken = authorization.slice('Bearer '.length).trim();

    if (!idToken) {
      throw new HttpError(401, 'Token de autenticação ausente', {
        code: 'UNAUTHORIZED'
      });
    }

    req.user = await verifyFirebaseToken(idToken);
    logger.info('Autenticacao validada', {
      requestId: req.requestId,
      method: req.method,
      path: req.originalUrl,
      uid: req.user.uid
    });
    next();
  } catch (error) {
    logger.warn('Falha de autenticacao', {
      requestId: req.requestId,
      method: req.method,
      path: req.originalUrl,
      error: error.message
    });

    if (error instanceof HttpError) {
      next(error);
      return;
    }

    next(
      new HttpError(401, 'Token de autenticação inválido', {
        code: 'UNAUTHORIZED'
      })
    );
  }
}

module.exports = {
  authenticateRequest
};
