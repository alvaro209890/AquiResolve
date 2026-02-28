class HttpError extends Error {
  constructor(statusCode, message, options = {}) {
    super(message);

    this.name = 'HttpError';
    this.statusCode = statusCode;
    this.code = options.code || 'HTTP_ERROR';
    this.details = options.details || null;
    this.expose = options.expose !== undefined ? options.expose : statusCode < 500;
  }
}

module.exports = HttpError;
