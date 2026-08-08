export enum OrderStatus {
    PENDING_PAYMENT = "PENDING_PAYMENT",
    PAYMENT_PROCESSING = "PAYMENT_PROCESSING",
    CONFIRMED = "CONFIRMED",
    PREPARING = "PREPARING",
    READY_FOR_PICKUP = "READY_FOR_PICKUP",
    OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY",
    DELIVERED = "DELIVERED",
    CANCELLED = "CANCELLED",
    PAYMENT_FAILED = "PAYMENT_FAILED"
}

export const validTransitions: Record<OrderStatus, OrderStatus[]> = {
    [OrderStatus.PENDING_PAYMENT]: [OrderStatus.PAYMENT_PROCESSING, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED],
    [OrderStatus.PAYMENT_PROCESSING]: [OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED],
    [OrderStatus.PAYMENT_FAILED]: [OrderStatus.PENDING_PAYMENT, OrderStatus.CANCELLED],
    [OrderStatus.CONFIRMED]: [OrderStatus.PREPARING, OrderStatus.CANCELLED],
    [OrderStatus.PREPARING]: [OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED],
    [OrderStatus.READY_FOR_PICKUP]: [OrderStatus.OUT_FOR_DELIVERY],
    [OrderStatus.OUT_FOR_DELIVERY]: [OrderStatus.DELIVERED],
    [OrderStatus.DELIVERED]: [],
    [OrderStatus.CANCELLED]: []
};

/**
 * Checks if transitioning from fromStatus to toStatus is permitted.
 */
export function canTransition(fromStatus: OrderStatus, toStatus: OrderStatus): boolean {
    const allowed = validTransitions[fromStatus];
    return allowed ? allowed.includes(toStatus) : false;
}

/**
 * Checks if the order can be cancelled by the customer from its current status.
 */
export function canCancel(currentStatus: OrderStatus): boolean {
    return currentStatus === OrderStatus.CONFIRMED || 
           currentStatus === OrderStatus.PREPARING || 
           currentStatus === OrderStatus.PENDING_PAYMENT || 
           currentStatus === OrderStatus.PAYMENT_PROCESSING || 
           currentStatus === OrderStatus.PAYMENT_FAILED;
}
