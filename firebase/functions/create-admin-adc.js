const admin = require("firebase-admin");

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: "foodfusionai-a0592"
});

async function run() {
    try {
        console.log("Checking if user exists...");
        const email = "admin@foodfusion.ai";
        let userRecord;
        try {
            userRecord = await admin.auth().getUserByEmail(email);
            console.log("User already exists:", userRecord.uid);
            await admin.auth().updateUser(userRecord.uid, { password: "Admin@123" });
            console.log("Password reset to Admin@123 just in case.");
        } catch (e) {
            console.log("Creating new user...");
            userRecord = await admin.auth().createUser({
                email: email,
                password: "Admin@123",
                displayName: "System Admin"
            });
            console.log("User created:", userRecord.uid);
        }

        console.log("Setting custom claims...");
        await admin.auth().setCustomUserClaims(userRecord.uid, { admin: true });

        console.log("Bootstrapping admin document in Firestore (Bypassing rules)...");
        await admin.firestore().collection("adminUsers").doc(userRecord.uid).set({
            email: userRecord.email,
            role: "SUPER_ADMIN",
            promotedAt: admin.firestore.FieldValue.serverTimestamp()
        });
        
        console.log("=========================================");
        console.log("SUCCESS! ADMIN BOOTSTRAP COMPLETE!");
        console.log("Email: admin@foodfusion.ai");
        console.log("Password: Admin@123");
        console.log("=========================================");
        
        // Let's also forcefully inject the dummy data for Phase 11 testing if it's empty
        console.log("Ensuring basic data exists for testing...");
        const restSnap = await admin.firestore().collection("restaurants").limit(1).get();
        if (restSnap.empty) {
             const newRest = await admin.firestore().collection("restaurants").add({
                 name: "Phase 11 Pizza",
                 address: "Test Address",
                 city: "Test City",
                 isOpen: true,
                 isApproved: true,
                 deliveryFee: 40
             });
             console.log("Created test restaurant:", newRest.id);
        }
        
        process.exit(0);
    } catch(e) {
        console.error(e);
        process.exit(1);
    }
}

run();
