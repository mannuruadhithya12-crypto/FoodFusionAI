"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.validTransitions = exports.OrderStatus = void 0;
exports.canTransition = canTransition;
exports.canCancel = canCancel;
var OrderStatus;
(function (OrderStatus) {
    OrderStatus["PENDING_PAYMENT"] = "PENDING_PAYMENT";
    OrderStatus["PAYMENT_PROCESSING"] = "PAYMENT_PROCESSING";
    OrderStatus["CONFIRMED"] = "CONFIRMED";
    OrderStatus["PREPARING"] = "PREPARING";
    OrderStatus["READY_FOR_PICKUP"] = "READY_FOR_PICKUP";
    OrderStatus["OUT_FOR_DELIVERY"] = "OUT_FOR_DELIVERY";
    OrderStatus["DELIVERED"] = "DELIVERED";
    OrderStatus["CANCELLED"] = "CANCELLED";
    OrderStatus["PAYMENT_FAILED"] = "PAYMENT_FAILED";
})(OrderStatus || (exports.OrderStatus = OrderStatus = {}));
exports.validTransitions = {
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
function canTransition(fromStatus, toStatus) {
    const allowed = exports.validTransitions[fromStatus];
    return allowed ? allowed.includes(toStatus) : false;
}
/**
 * Checks if the order can be cancelled by the customer from its current status.
 */
function canCancel(currentStatus) {
    return currentStatus === OrderStatus.CONFIRMED ||
        currentStatus === OrderStatus.PREPARING ||
        currentStatus === OrderStatus.PENDING_PAYMENT ||
        currentStatus === OrderStatus.PAYMENT_PROCESSING ||
        currentStatus === OrderStatus.PAYMENT_FAILED;
}
//# sourceMappingURL=orderStateMachine.js.map