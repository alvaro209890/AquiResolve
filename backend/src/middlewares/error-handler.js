const HttpError = require('../utils/http-error');
const logger = require('../utils/logger');

function buildMessage(statusCode) {
  if (statusCode >= 500) {
    return 'Erro interno ao processar a solicitação';
  }

  return 'Solicitação inválida';
}

function notFoundHandler(req, _res, next) {
  next(
    new HttpError(404, `Rota não encontrada: ${req.method} ${req.originalUrl}`, {
      code: 'NOT_FOUND'
    })
  );
}

function errorHandler(error, req, res, _next) {
  const statusCode = Number.isInteger(error.statusCode) ? error.statusCode : 500;
  const errorCode = error.code || 'INTERNAL_ERROR';
  const safeMessage = error.expose ? error.message : buildMessage(statusCode);

  const logMethod = statusCode >= 500 ? logger.error : logger.warn;
  logMethod('Requisicao finalizada com erro', {
    requestId: req.requestId,
    method: req.method,
    path: req.originalUrl,
    statusCode,
    errorCode,
    message: error.message
  });

  res.status(statusCode).json({
    error: {
      code: errorCode,
      message: safeMessage,
      details: error.details || null
    }
  });
}

module.exports = {
  notFoundHandler,
  errorHandler
};
