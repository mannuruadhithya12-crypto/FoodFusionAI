import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const getRecommendations = functions.https.onCall(async (data, context) => {
    // Enforce Authentication
    if (!context.auth || !context.auth.uid) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to get recommendations.");
    }
    
    const uid = context.auth.uid;
    const db = admin.firestore();
    
    // We fetch top trending foods from the 'foods' collection
    const foodsSnapshot = await db.collection("foods")
        .where("isAvailable", "==", true)
        .orderBy("rating", "desc")
        .limit(20)
        .get();

    const popularFoods = foodsSnapshot.docs.map(doc => {
        const d = doc.data();
        d.id = doc.id;
        return d;
    });

    // Try to get user preferences or past orders
    try {
        const ordersSnapshot = await db.collection("orders")
            .where("userId", "==", uid)
            .orderBy("createdAt", "desc")
            .limit(5)
            .get();

        if (ordersSnapshot.empty) {
            return {
                recommendations: popularFoods.slice(0, 5),
                reason: "Trending today"
            };
        }

        // Extract commonly ordered categories or foods
        const orderedItemNames = new Set<string>();
        ordersSnapshot.docs.forEach(doc => {
            const items = doc.data().items || [];
            items.forEach((item: any) => {
                if (item.foodName) {
                    orderedItemNames.add(item.foodName.toLowerCase());
                }
            });
        });

        // Filter popular foods that might match user's past orders or just mix them up
        // Here we build a personalized list: mix of what they ordered, and popular items
        const personalized = popularFoods.filter(food => {
            const nameLower = food.name?.toLowerCase() || "";
            // Find foods that have some keyword overlap, or just recommend popular
            return Array.from(orderedItemNames).some(ordered => ordered.includes(nameLower) || nameLower.includes(ordered));
        });

        if (personalized.length > 0) {
            // Fill the rest with popular foods if we don't have enough personalized
            const finalRecs = [...personalized];
            popularFoods.forEach(pf => {
                if (!finalRecs.find(f => f.id === pf.id) && finalRecs.length < 5) {
                    finalRecs.push(pf);
                }
            });

            return {
                recommendations: finalRecs,
                reason: "Based on your past orders"
            };
        }

        // Fallback to popular if no strong correlation
        return {
            recommendations: popularFoods.slice(0, 5),
            reason: "Trending today"
        };
    } catch (e) {
        console.error("Error generating personalized recommendations:", e);
        // Fallback to generic popular
        return {
            recommendations: popularFoods.slice(0, 5),
            reason: "Trending today"
        };
    }
});
