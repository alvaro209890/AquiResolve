function buildLogEntry(level, message, meta) {
  const entry = {
    ts: new Date().toISOString(),
    level,
    message
  };

  if (meta && typeof meta === 'object' && Object.keys(meta).length > 0) {
    entry.meta = meta;
  }

  return JSON.stringify(entry);
}

function info(message, meta) {
  console.log(buildLogEntry('info', message, meta));
}

function warn(message, meta) {
  console.warn(buildLogEntry('warn', message, meta));
}

function error(message, meta) {
  console.error(buildLogEntry('error', message, meta));
}

module.exports = {
  info,
  warn,
  error
};
