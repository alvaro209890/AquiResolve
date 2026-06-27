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

  const gatewayErrors = responseData.errors;
  const firstGatewayError = Array.isArray(gatewayErrors) ? gatewayErrors[0] : null;

  if (
    firstGatewayError &&
    typeof firstGatewayError === 'object' &&
    typeof firstGatewayError.message === 'string' &&
    firstGatewayError.message.trim()
  ) {
    return firstGatewayError.message.trim();
  }

  if (gatewayErrors && typeof gatewayErrors === 'object' && !Array.isArray(gatewayErrors)) {
    const details = Object.entries(gatewayErrors)
      .flatMap(([field, value]) => {
        if (Array.isArray(value)) {
          return value.map((message) => ({ field, message }));
        }
        return [{ field, message: value }];
      })
      .map(({ field, message }) => {
        const normalizedMessage =
          typeof message === 'string'
            ? message.trim()
            : message && typeof message === 'object' && typeof message.message === 'string'
              ? message.message.trim()
              : '';

        return normalizedMessage ? `${field}: ${normalizedMessage}` : '';
      })
      .filter(Boolean);

    if (details.length > 0) {
      return details.slice(0, 3).join('; ');
    }
  }

  return null;
}

function extractGatewayDetails(error) {
  const responseData = error.response && error.response.data;

  if (!responseData || typeof responseData !== 'object') {
    return null;
  }

  const gatewayErrors = responseData.errors;
  if (!gatewayErrors) {
    return null;
  }

  if (Array.isArray(gatewayErrors)) {
    return gatewayErrors.slice(0, 5).map((item) => ({
      message: typeof item?.message === 'string' ? item.message : String(item || ''),
      parameter: typeof item?.parameter_name === 'string' ? item.parameter_name : null
    }));
  }

  if (typeof gatewayErrors === 'object') {
    return Object.entries(gatewayErrors)
      .slice(0, 8)
      .map(([field, value]) => ({
        field,
        messages: Array.isArray(value) ? value.map(String).slice(0, 5) : [String(value)]
      }));
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
        code: 'PAYMENT_FAILED',
        details: extractGatewayDetails(error)
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
