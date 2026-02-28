const { loadEnv } = require('./config/env');
const { initializeFirebase } = require('./config/firebase');
const { createApp } = require('./app');

const config = loadEnv();
initializeFirebase();

const app = createApp({ config });

app.listen(config.port, () => {
  console.log(`Payments backend listening on port ${config.port}`);
});
