"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.getRecommendations = void 0;
const functions = __importStar(require("firebase-functions"));
const admin = __importStar(require("firebase-admin"));
exports.getRecommendations = functions.https.onCall(async (data, context) => {
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
        const orderedItemNames = new Set();
        ordersSnapshot.docs.forEach(doc => {
            const items = doc.data().items || [];
            items.forEach((item) => {
                if (item.foodName) {
                    orderedItemNames.add(item.foodName.toLowerCase());
                }
            });
        });
        // Filter popular foods that might match user's past orders or just mix them up
        // Here we build a personalized list: mix of what they ordered, and popular items
        const personalized = popularFoods.filter(food => {
            var _a;
            const nameLower = ((_a = food.name) === null || _a === void 0 ? void 0 : _a.toLowerCase()) || "";
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
    }
    catch (e) {
        console.error("Error generating personalized recommendations:", e);
        // Fallback to generic popular
        return {
            recommendations: popularFoods.slice(0, 5),
            reason: "Trending today"
        };
    }
});
//# sourceMappingURL=getRecommendations.js.map