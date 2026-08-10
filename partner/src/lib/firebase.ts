import { initializeApp, getApps } from "firebase/app";
import { getAuth } from "firebase/auth";
import { getFirestore } from "firebase/firestore";
import { getStorage } from "firebase/storage";
import { getFunctions } from "firebase/functions";

const firebaseConfig = {
  apiKey: "AIzaSyBRvpadrQZWhpr_sFQuWyJVBhBsh13Yijo",
  authDomain: "foodfusionai-a0592.firebaseapp.com",
  projectId: "foodfusionai-a0592",
  storageBucket: "foodfusionai-a0592.appspot.com",
  messagingSenderId: "146660841956",
  appId: "1:146660841956:web:1234567890abcdef"
};

const app = getApps().length === 0 ? initializeApp(firebaseConfig) : getApps()[0];

export const auth = getAuth(app);
export const db = getFirestore(app);
export const storage = getStorage(app);
export const functions = getFunctions(app);
