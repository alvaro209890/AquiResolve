const express = require('express');

const { authenticateRequest } = require('../middlewares/auth');
const {
  processCardPayment,
  processPixPayment,
  getPaymentStatus
} = require('../controllers/payments.controller');

const router = express.Router();

router.use(authenticateRequest);

router.post('/card', processCardPayment);
router.post('/pix', processPixPayment);
router.get('/:orderId/status', getPaymentStatus);

module.exports = router;
