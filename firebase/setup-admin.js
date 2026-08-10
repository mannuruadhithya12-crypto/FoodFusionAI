const admin = require('firebase-admin');
const fs = require('fs');

const serviceAccountPath = '../app/google-services.json'; // Just for project info, actually we need standard Application Default Credentials.
// To run this: set GOOGLE_APPLICATION_CREDENTIALS=... or just use a token if not available.
// Actually, since I have the emulator or if I'm just running against live firestore:
// Wait, I don't have the service account key!
// Let me just write this script that the user can use if they want, OR I can use the Web SDK to do it in an insecure way since firestore rules are currently open until we lock it down? No, I already locked it down: `allow write: if false;` for adminUsers.

// Let's modify firestore.rules temporarily to allow me to create the admin record, OR I'll add a Cloud Function that checks a secret.
