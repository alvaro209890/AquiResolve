const admin = require('firebase-admin');

const { loadEnv } = require('./env');

function initializeFirebase() {
  if (admin.apps.length > 0) {
    return admin;
  }

  const config = loadEnv();

  admin.initializeApp({
    credential: admin.credential.cert({
      projectId: config.firebaseProjectId,
      clientEmail: config.firebaseClientEmail,
      privateKey: config.firebasePrivateKey
    }),
    projectId: config.firebaseProjectId
  });

  return admin;
}

async function verifyFirebaseToken(idToken) {
  const firebaseAdmin = initializeFirebase();
  return firebaseAdmin.auth().verifyIdToken(idToken);
}

module.exports = {
  initializeFirebase,
  verifyFirebaseToken
};
