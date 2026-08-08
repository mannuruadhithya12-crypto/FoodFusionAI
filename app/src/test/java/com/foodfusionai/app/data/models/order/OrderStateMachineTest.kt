package com.foodfusionai.app.data.models.order

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderStateMachineTest {

    @Test
    fun `test valid transitions`() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.PREPARING))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY))
        assertTrue(OrderStateMachine.canTransition(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED))
    }

    @Test
    fun `test invalid transitions`() {
        assertFalse(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.DELIVERED))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.PREPARING))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.DELIVERED, OrderStatus.CANCELLED))
        assertFalse(OrderStateMachine.canTransition(OrderStatus.CANCELLED, OrderStatus.PREPARING))
    }

    @Test
    fun `test valid cancellations`() {
        assertTrue(OrderStateMachine.canCancel(OrderStatus.CONFIRMED))
        assertTrue(OrderStateMachine.canCancel(OrderStatus.PREPARING))
        assertTrue(OrderStateMachine.canCancel(OrderStatus.PENDING_PAYMENT))
    }

    @Test
    fun `test invalid cancellations`() {
        assertFalse(OrderStateMachine.canCancel(OrderStatus.READY_FOR_PICKUP))
        assertFalse(OrderStateMachine.canCancel(OrderStatus.OUT_FOR_DELIVERY))
        assertFalse(OrderStateMachine.canCancel(OrderStatus.DELIVERED))
        assertFalse(OrderStateMachine.canCancel(OrderStatus.CANCELLED))
    }
}
