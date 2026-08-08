/**
 * Converts INR (Rupees) to Paise.
 * Razorpay expects amounts in the smallest currency unit.
 * @param amountInRupees The amount in INR (e.g. 100.50)
 * @returns The amount in Paise (e.g. 10050)
 */
export function convertRupeesToPaise(amountInRupees: number): number {
    if (amountInRupees <= 0) throw new Error("Amount must be greater than zero.");
    if (!isFinite(amountInRupees)) throw new Error("Amount must be a finite number.");
    
    // Convert securely to avoid floating point precision issues (e.g. 100.50 * 100 = 10050)
    return Math.round(amountInRupees * 100);
}
