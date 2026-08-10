import { initializeApp } from "firebase/app";
import { getAuth, createUserWithEmailAndPassword, signInWithEmailAndPassword } from "firebase/auth";
import { getFirestore, doc, setDoc } from "firebase/firestore";

const firebaseConfig = {
  apiKey: "AIzaSyBRvpadrQZWhpr_sFQuWyJVBhBsh13Yijo",
  authDomain: "foodfusionai-a0592.firebaseapp.com",
  projectId: "foodfusionai-a0592",
  storageBucket: "foodfusionai-a0592.firebasestorage.app",
  messagingSenderId: "146660841956",
  appId: "1:146660841956:web:1234567890abcdef"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

async function run() {
    try {
        console.log("Creating user...");
        let user;
        try {
            const cred = await createUserWithEmailAndPassword(auth, "admin@foodfusion.ai", "Admin@123");
            user = cred.user;
        } catch {
            console.log("User might already exist, trying to sign in...");
            const cred = await signInWithEmailAndPassword(auth, "admin@foodfusion.ai", "Admin@123");
            user = cred.user;
        }
        
        console.log("User created/signed in:", user.uid);

        console.log("Bootstrapping admin document...");
        await setDoc(doc(db, "adminUsers", user.uid), {
            email: user.email,
            role: "SUPER_ADMIN",
            promotedAt: new Date().toISOString()
        });
        
        console.log("Admin successfully bootstrapped!");
        process.exit(0);
    } catch(e) {
        console.error(e);
        process.exit(1);
    }
}

run();
