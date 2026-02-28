const HttpError = require('../utils/http-error');

function normalizeOrderResponse(orderData) {
  return orderData;
}

function extractGatewayMessage(error) {
  const responseData = error.response && error.response.data;

  if (!responseData || typeof responseData !== 'object') {
    return null;
  }

  if (typeof responseData.message === 'string' && responseData.message.trim()) {
    return responseData.message.trim();
  }

  const firstGatewayError = Array.isArray(responseData.errors) ? responseData.errors[0] : null;

  if (
    firstGatewayError &&
    typeof firstGatewayError === 'object' &&
    typeof firstGatewayError.message === 'string' &&
    firstGatewayError.message.trim()
  ) {
    return firstGatewayError.message.trim();
  }

  return null;
}

function mapPagarmeError(error) {
  if (error instanceof HttpError) {
    return error;
  }

  if (error.response) {
    const status = error.response.status;
    const gatewayMessage = extractGatewayMessage(error);

    if (status === 400 || status === 404 || status === 422) {
      return new HttpError(status, gatewayMessage || 'Requisição rejeitada pelo gateway', {
        code: 'PAYMENT_FAILED'
      });
    }

    if (status === 401 || status === 403) {
      return new HttpError(502, 'Falha de autenticação com o gateway de pagamento', {
        code: 'PAYMENT_GATEWAY_AUTH_FAILED'
      });
    }

    return new HttpError(502, gatewayMessage || 'Erro ao processar pagamento no gateway', {
      code: 'PAYMENT_GATEWAY_ERROR'
    });
  }

  return new HttpError(500, 'Erro interno ao processar pagamento', {
    code: 'INTERNAL_ERROR'
  });
}

module.exports = {
  normalizeOrderResponse,
  mapPagarmeError
};
