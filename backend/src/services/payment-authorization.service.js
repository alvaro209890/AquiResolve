const crypto = require('crypto');

const HttpError = require('../utils/http-error');
const { initializeFirebase } = require('../config/firebase');

const CART_CHECKOUT_CODE = 'cart_checkout';
const ORDER_STATUS_AWAITING_PAYMENT = 'awaiting_payment';
const ORDER_STATUS_DRAFT = 'draft';
const PAYMENT_STATUS_PAID = 'paid';
const PAYMENT_STATUS_PENDING = 'pending';
const GATEWAY_CODE_MAX_LENGTH = 52;

function normalizeString(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function normalizeDigits(value) {
  return normalizeString(value).replace(/\D/g, '');
}

function buildGatewayCode(localOrderCode) {
  const normalized = normalizeString(localOrderCode);
  const compact = normalized.replace(/[^a-zA-Z0-9_-]/g, '_');

  if (compact && compact.length <= GATEWAY_CODE_MAX_LENGTH) {
    return compact;
  }

  const hash = crypto.createHash('sha256').update(normalized || String(Date.now())).digest('hex').slice(0, 24);
  return `aqr_${hash}`;
}

function isCartCheckoutCode(orderId) {
  return orderId === CART_CHECKOUT_CODE || orderId.startsWith(`${CART_CHECKOUT_CODE}_`);
}

function normalizeLower(value) {
  return normalizeString(value).toLowerCase();
}

function toCents(amount) {
  if (typeof amount !== 'number' || !Number.isFinite(amount) || amount <= 0) {
    throw new HttpError(422, 'Valor do pedido invalido para pagamento', {
      code: 'INVALID_ORDER_AMOUNT'
    });
  }

  // Multiplicação inteira para evitar imprecisão IEEE 754
  // Ex: R$ 19.90 → "19.90" → 19.90 * 100 = 1989.9999... → Math.round = 1989 ❌
  // Com string: "1990" → 1990 ✅
  const amountFixed = Number(amount.toFixed(2));
  const cents = Math.round(amountFixed * 100);

  if (!Number.isFinite(cents) || cents <= 0) {
    throw new HttpError(422, 'Valor do pedido invalido para pagamento', {
      code: 'INVALID_ORDER_AMOUNT'
    });
  }

  return cents;
}

function clonePayments(payments) {
  const sanitizedPayments = Array.isArray(payments)
    ? payments
        .filter((payment) => payment && typeof payment === 'object' && !Array.isArray(payment))
        .map((payment) => ({ ...payment }))
    : [];

  if (sanitizedPayments.length === 0) {
    throw new HttpError(400, 'O campo payments e obrigatorio', {
      code: 'INVALID_PAYLOAD'
    });
  }

  return sanitizedPayments;
}

function trimGatewayText(value, maxLength) {
  const normalized = normalizeString(value);
  return normalized.length > maxLength ? normalized.slice(0, maxLength) : normalized;
}

function sanitizePixAdditionalInformation(pix, gatewayCode) {
  if (!pix || typeof pix !== 'object' || Array.isArray(pix)) {
    return pix;
  }

  const sanitizedPix = { ...pix };
  const additionalInformation = Array.isArray(sanitizedPix.additional_information)
    ? sanitizedPix.additional_information
    : Array.isArray(sanitizedPix.additionalInformation)
      ? sanitizedPix.additionalInformation
      : null;

  if (!additionalInformation) {
    return sanitizedPix;
  }

  sanitizedPix.additional_information = additionalInformation
    .filter((item) => item && typeof item === 'object' && !Array.isArray(item))
    .slice(0, 5)
    .map((item) => ({
      name: trimGatewayText(item.name || 'Pedido', 50) || 'Pedido',
      value: trimGatewayText(item.value || gatewayCode, 50) || gatewayCode
    }));
  delete sanitizedPix.additionalInformation;

  return sanitizedPix;
}

function parseCardExpiration(card) {
  const explicitMonth = Number(card.exp_month || card.expMonth);
  const explicitYear = Number(card.exp_year || card.expYear);

  if (Number.isInteger(explicitMonth) && Number.isInteger(explicitYear)) {
    return {
      exp_month: explicitMonth,
      exp_year: explicitYear
    };
  }

  const compactExpiration = normalizeDigits(card.card_expiration_date || card.cardExpirationDate);
  if (compactExpiration.length < 4) {
    return {
      exp_month: explicitMonth,
      exp_year: explicitYear
    };
  }

  const month = Number(compactExpiration.slice(0, 2));
  const year = Number(compactExpiration.slice(2, 4));

  return {
    exp_month: month,
    exp_year: year
  };
}

function sanitizeCard(card, billingAddress) {
  if (!card || typeof card !== 'object' || Array.isArray(card)) {
    return card;
  }

  const expiration = parseCardExpiration(card);
  const sanitizedCard = {
    ...card,
    number: normalizeDigits(card.number || card.card_number || card.cardNumber),
    holder_name: normalizeString(card.holder_name || card.holderName || card.card_holder_name || card.cardHolderName),
    exp_month: expiration.exp_month,
    exp_year: expiration.exp_year,
    cvv: normalizeDigits(card.cvv || card.card_cvv || card.cardCvv)
  };

  delete sanitizedCard.card_number;
  delete sanitizedCard.cardNumber;
  delete sanitizedCard.card_holder_name;
  delete sanitizedCard.cardHolderName;
  delete sanitizedCard.card_expiration_date;
  delete sanitizedCard.cardExpirationDate;
  delete sanitizedCard.card_cvv;
  delete sanitizedCard.cardCvv;
  delete sanitizedCard.holderName;
  delete sanitizedCard.expMonth;
  delete sanitizedCard.expYear;

  const normalizedBillingAddress = sanitizeAddress(card.billing_address || card.billingAddress, billingAddress);
  if (normalizedBillingAddress) {
    sanitizedCard.billing_address = normalizedBillingAddress;
  }
  delete sanitizedCard.billingAddress;

  return sanitizedCard;
}

function sanitizeCreditCardPayment(payment) {
  const rawCreditCard = payment.credit_card || payment.creditCard;
  if (!rawCreditCard || typeof rawCreditCard !== 'object' || Array.isArray(rawCreditCard)) {
    return payment;
  }

  const billingAddress = sanitizeAddress(rawCreditCard.billing_address || rawCreditCard.billingAddress);
  const sanitizedCreditCard = {
    ...rawCreditCard,
    installments: Number(rawCreditCard.installments || 1),
    card: sanitizeCard(rawCreditCard.card, billingAddress)
  };

  delete sanitizedCreditCard.billing_address;
  delete sanitizedCreditCard.billingAddress;

  return {
    ...payment,
    payment_method: 'credit_card',
    credit_card: sanitizedCreditCard
  };
}

function preparePaymentsForGateway(payments, gatewayCode) {
  return clonePayments(payments).map((payment) => {
    const paymentMethod = normalizeString(payment.payment_method || payment.paymentMethod);

    if (paymentMethod === 'credit_card') {
      return sanitizeCreditCardPayment(payment);
    }

    if (paymentMethod !== 'pix') {
      return payment;
    }

    return {
      ...payment,
      payment_method: 'pix',
      pix: sanitizePixAdditionalInformation(payment.pix, gatewayCode)
    };
  });
}

function buildMetadata(metadata, orderId, source) {
  const baseMetadata =
    metadata && typeof metadata === 'object' && !Array.isArray(metadata)
      ? { ...metadata }
      : {};

  return {
    ...baseMetadata,
    order_id: orderId,
    payment_source: source,
    platform: normalizeString(baseMetadata.platform) || 'android'
  };
}

function sanitizeAddress(address, fallback = {}) {
  const source =
    address && typeof address === 'object' && !Array.isArray(address)
      ? address
      : {};
  const fallbackAddress =
    fallback && typeof fallback === 'object' && !Array.isArray(fallback)
      ? fallback
      : {};

  const line1 = normalizeString(source.line_1 || source.line1) ||
    normalizeString(fallbackAddress.line_1 || fallbackAddress.line1);
  const zipCode = normalizeDigits(source.zip_code || source.zipCode) ||
    normalizeDigits(fallbackAddress.zip_code || fallbackAddress.zipCode);
  const city = normalizeString(source.city) || normalizeString(fallbackAddress.city);
  const state = normalizeString(source.state) || normalizeString(fallbackAddress.state);
  const country = normalizeString(source.country) || normalizeString(fallbackAddress.country) || 'BR';

  if (!line1 || !zipCode || !city || !state) {
    return null;
  }

  return {
    line_1: line1,
    line_2: normalizeString(source.line_2 || source.line2 || fallbackAddress.line_2 || fallbackAddress.line2) || undefined,
    zip_code: zipCode,
    city,
    state,
    country
  };
}

function extractBillingAddressFromPayments(payments) {
  const firstPayment = Array.isArray(payments) ? payments[0] : null;
  if (!firstPayment || typeof firstPayment !== 'object' || Array.isArray(firstPayment)) {
    return null;
  }

  return firstPayment.credit_card?.billing_address ||
    firstPayment.credit_card?.card?.billing_address ||
    firstPayment.creditCard?.billingAddress ||
    firstPayment.creditCard?.card?.billingAddress ||
    null;
}

function sanitizeCustomer(customer, fallback = {}) {
  if (!customer || typeof customer !== 'object' || Array.isArray(customer)) {
    throw new HttpError(400, 'O campo customer e obrigatorio', {
      code: 'INVALID_PAYLOAD'
    });
  }

  const sanitizedCustomer = { ...customer };
  sanitizedCustomer.name = normalizeString(sanitizedCustomer.name) || normalizeString(fallback.name);
  sanitizedCustomer.email = normalizeString(sanitizedCustomer.email) || normalizeString(fallback.email);
  sanitizedCustomer.document = normalizeDigits(sanitizedCustomer.document || fallback.document);

  const address = sanitizeAddress(sanitizedCustomer.address, fallback.address);
  if (address) {
    sanitizedCustomer.address = address;
  } else {
    delete sanitizedCustomer.address;
  }

  if (!sanitizedCustomer.name || !sanitizedCustomer.email) {
    throw new HttpError(422, 'Dados do cliente incompletos para o pagamento', {
      code: 'INVALID_CUSTOMER_DATA'
    });
  }

  return sanitizedCustomer;
}

function extractRequestedOrderId(payload) {
  const metadataOrderId = normalizeString(payload?.metadata?.order_id);
  const itemCode = normalizeString(payload?.items?.[0]?.code);

  if (metadataOrderId && itemCode && metadataOrderId !== itemCode) {
    throw new HttpError(400, 'Identificador do pedido inconsistente no payload', {
      code: 'INVALID_ORDER_CODE'
    });
  }

  const orderId = metadataOrderId || itemCode;
  if (!orderId) {
    throw new HttpError(400, 'Identificador do pedido ausente no payload', {
      code: 'INVALID_ORDER_CODE'
    });
  }

  return orderId;
}

function buildOrderDescription(order) {
  const serviceName = normalizeString(order?.serviceName);
  const serviceType = normalizeString(order?.serviceType);
  const parts = [serviceName, serviceType].filter(Boolean);
  return parts.join(' - ') || 'Pedido AquiResolve';
}

async function loadUserFallbackData(firestore, uid) {
  const userSnapshot = await firestore.collection('users').doc(uid).get();
  if (!userSnapshot.exists) {
    return {};
  }

  const data = userSnapshot.data() || {};
  return {
    name: normalizeString(data.fullName),
    email: normalizeString(data.email),
    document: normalizeDigits(data.cpf)
  };
}

function buildAddressFallbackFromOrder(order) {
  return {
    line1: normalizeString(order?.address) || normalizeString(order?.originAddress),
    line2: normalizeString(order?.complement),
    zipCode: normalizeDigits(order?.zipCode || order?.cep),
    city: normalizeString(order?.city),
    state: normalizeString(order?.state),
    country: 'BR'
  };
}

function buildGatewayPayload({ localOrderCode, description, amount, customer, payments, metadata, source, closed }) {
  const gatewayCode = buildGatewayCode(localOrderCode);

  return {
    code: gatewayCode,
    items: [
      {
        amount: toCents(amount),
        description,
        quantity: 1,
        code: gatewayCode
      }
    ],
    customer,
    payments: preparePaymentsForGateway(payments, gatewayCode),
    metadata: buildMetadata(metadata, localOrderCode, source),
    closed: closed !== false
  };
}

async function authorizeSingleOrderPayload({ firestore, payload, uid, orderId }) {
  const orderSnapshot = await firestore.collection('orders').doc(orderId).get();
  if (!orderSnapshot.exists) {
    throw new HttpError(404, 'Pedido nao encontrado para pagamento', {
      code: 'ORDER_NOT_FOUND'
    });
  }

  const order = orderSnapshot.data() || {};
  if (normalizeString(order.clientId) !== uid) {
    throw new HttpError(403, 'Voce nao tem acesso a este pedido', {
      code: 'FORBIDDEN_ORDER'
    });
  }

  const orderStatus = normalizeLower(order.status);
  const paymentStatus = normalizeLower(order.paymentStatus);

  if (paymentStatus === PAYMENT_STATUS_PAID) {
    throw new HttpError(409, 'Este pedido ja foi pago', {
      code: 'ORDER_ALREADY_PAID'
    });
  }

  const canBePaid =
    (orderStatus === ORDER_STATUS_AWAITING_PAYMENT || orderStatus === ORDER_STATUS_DRAFT) &&
    (!paymentStatus ||
      paymentStatus === ORDER_STATUS_AWAITING_PAYMENT ||
      paymentStatus === PAYMENT_STATUS_PENDING);

  if (!canBePaid) {
    throw new HttpError(409, 'Pedido nao esta disponivel para um novo pagamento', {
      code: 'ORDER_PAYMENT_STATE_INVALID'
    });
  }

  const customer = sanitizeCustomer(payload.customer, {
      name: order.clientName,
      email: order.clientEmail,
      document: order.clientCpf || order.cpf,
      address: sanitizeAddress(
        null,
        buildAddressFallbackFromOrder(order)
      ) || sanitizeAddress(extractBillingAddressFromPayments(payload.payments))
    });

  return buildGatewayPayload({
    localOrderCode: orderId,
    description: buildOrderDescription(order),
    amount: Number(order.finalPrice || order.estimatedPrice),
    customer,
    payments: payload.payments,
    metadata: payload.metadata,
    source: 'firestore_order',
    closed: payload.closed
  });
}

async function authorizeCartPayload({ firestore, payload, uid, requestedOrderId }) {
  const fallbackCustomer = await loadUserFallbackData(firestore, uid);
  const cartOrderCode =
    requestedOrderId && requestedOrderId !== CART_CHECKOUT_CODE
      ? requestedOrderId
      : `${CART_CHECKOUT_CODE}_${uid}_${Date.now()}`;
  let payableItems = [];
  let paymentSource = 'cart_checkout';

  if (requestedOrderId && requestedOrderId !== CART_CHECKOUT_CODE) {
    const checkoutOrdersSnapshot = await firestore
      .collection('orders')
      .where('cartCheckoutCode', '==', cartOrderCode)
      .get();

    payableItems = checkoutOrdersSnapshot.docs
      .map((doc) => doc.data() || {})
      .filter((order) => normalizeString(order.clientId) === uid)
      .filter((order) => normalizeLower(order.status) === ORDER_STATUS_AWAITING_PAYMENT);

    if (payableItems.length > 0) {
      paymentSource = 'prepared_cart_checkout';
    }
  }

  if (payableItems.length === 0) {
    const cartSnapshot = await firestore.collection('carts').doc(uid).collection('items').get();
    if (cartSnapshot.empty) {
      throw new HttpError(422, 'Carrinho vazio para pagamento', {
        code: 'EMPTY_CART'
      });
    }

    payableItems = cartSnapshot.docs.map((doc) => doc.data() || {});
  }

  const totalAmount = payableItems.reduce((sum, item) => {
    const itemAmount = Number(item.finalPrice || item.estimatedPrice || 0);
    return sum + (Number.isFinite(itemAmount) ? itemAmount : 0);
  }, 0);

  const firstPayableItem = payableItems[0] || {};
  const customer = sanitizeCustomer(payload.customer, {
    ...fallbackCustomer,
    address: sanitizeAddress(
      null,
      buildAddressFallbackFromOrder(firstPayableItem)
    ) || sanitizeAddress(extractBillingAddressFromPayments(payload.payments))
  });

  return buildGatewayPayload({
    localOrderCode: cartOrderCode,
    description: `Carrinho (${payableItems.length} servicos)`,
    amount: totalAmount,
    customer,
    payments: payload.payments,
    metadata: payload.metadata,
    source: paymentSource,
    closed: payload.closed
  });
}

async function authorizePaymentPayload({ payload, uid }) {
  const admin = initializeFirebase();
  const firestore = admin.firestore();
  const requestedOrderId = extractRequestedOrderId(payload);

  if (isCartCheckoutCode(requestedOrderId)) {
    return authorizeCartPayload({ firestore, payload, uid, requestedOrderId });
  }

  return authorizeSingleOrderPayload({
    firestore,
    payload,
    uid,
    orderId: requestedOrderId
  });
}

module.exports = {
  authorizePaymentPayload
};
