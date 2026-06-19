const { calculateServicePricing } = require('../services/service-pricing.service');
const logger = require('../utils/logger');

async function calculatePricing(req, res, next) {
  try {
    const result = await calculateServicePricing({
      category: req.body && req.body.category,
      serviceType: req.body && req.body.serviceType,
      distanceKm: req.body && req.body.distanceKm
    });

    logger.info('Precificacao de servico calculada', {
      requestId: req.requestId,
      uid: req.user && req.user.uid,
      category: result.category,
      serviceType: result.serviceType,
      source: result.source
    });

    res.status(200).json(result);
  } catch (error) {
    next(error);
  }
}

module.exports = {
  calculatePricing
};
