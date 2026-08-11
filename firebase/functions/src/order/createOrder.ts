import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

export const createOrder = functions.https.onCall(async (data, context) => {
    if (!context.auth) {
        throw new functions.https.HttpsError("unauthenticated", "User must be authenticated to create an order.");
    }

    const { restaurantId, items, couponCode, deliveryAddress } = data;
    
    if (!restaurantId || !items || !Array.isArray(items) || items.length === 0) {
        throw new functions.https.HttpsError("invalid-argument", "Valid restaurantId and items are required.");
    }
    
    if (!deliveryAddress) {
        throw new functions.https.HttpsError("invalid-argument", "Delivery address is required.");
    }

    const db = admin.firestore();
    let subtotal = 0;
    const orderItems: any[] = [];

    // Verify items and calculate subtotal securely from backend
    for (const item of items) {
        const foodDoc = await db.collection("foods").doc(item.foodId).get();
        if (!foodDoc.exists) {
            throw new functions.https.HttpsError("not-found", `Food item ${item.foodId} not found.`);
        }
        
        const foodData = foodDoc.data();
        if (foodData?.restaurantId !== restaurantId) {
             throw new functions.https.HttpsError("invalid-argument", `Food item ${item.foodId} does not belong to the selected restaurant.`);
        }
        if (!foodData?.isAvailable) {
             throw new functions.https.HttpsError("failed-precondition", `Food item ${foodData?.name} is out of stock.`);
        }
        
        const price = foodData?.price || 0;
        const quantity = item.quantity || 1;
        
        subtotal += price * quantity;
        orderItems.push({
            foodId: item.foodId,
            name: foodData?.name,
            price: price,
            quantity: quantity,
            imageUrl: foodData?.imageUrl || ""
        });
    }

    // Apply delivery fee logic (e.g., free above 500)
    let deliveryFee = subtotal >= 500 ? 0 : 40;
    
    // Apply discount
    let discount = 0;
    if (couponCode) {
        const couponQuery = await db.collection("coupons")
            .where("code", "==", couponCode.toUpperCase())
            .limit(1)
            .get();

        if (!couponQuery.empty) {
            const couponData = couponQuery.docs[0].data();
            if (couponData.isActive && (!couponData.validUntil || couponData.validUntil >= Date.now())) {
                if (!couponData.minOrderAmount || subtotal >= couponData.minOrderAmount) {
                    if (couponData.discountPercentage) {
                        let calcDiscount = subtotal * (couponData.discountPercentage / 100);
                        if (couponData.maxDiscountAmount && calcDiscount > couponData.maxDiscountAmount) {
                            calcDiscount = couponData.maxDiscountAmount;
                        }
                        discount = calcDiscount;
                    }
                }
            }
        }
    }
    
    const totalAmount = subtotal + deliveryFee - discount;
    const timestamp = Date.now();
    
    const newOrderRef = db.collection("orders").doc();
    
    const orderData = {
        userId: context.auth.uid,
        restaurantId: restaurantId,
        items: orderItems,
        orderStatus: "PENDING",
        paymentStatus: "PENDING",
        subtotal: subtotal,
        deliveryFee: deliveryFee,
        discount: discount,
        totalAmount: totalAmount,
        deliveryAddress: deliveryAddress,
        createdAt: timestamp,
        updatedAt: timestamp
    };
    
    await newOrderRef.set(orderData);
    
    return {
        orderId: newOrderRef.id,
        ...orderData
    };
});
