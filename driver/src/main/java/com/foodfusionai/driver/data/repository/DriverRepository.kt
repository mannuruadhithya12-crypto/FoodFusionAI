package com.foodfusionai.driver.data.repository

import com.foodfusionai.driver.data.models.Driver
import com.foodfusionai.driver.data.models.Order
import kotlinx.coroutines.flow.Flow

interface DriverRepository {
    fun login(email: String, password: String): Flow<Result<Driver>>
    fun register(email: String, password: String, profile: Driver): Flow<Result<Driver>>
    fun getDriverProfile(driverId: String): Flow<Result<Driver>>
    fun observeDriverProfile(driverId: String): Flow<Result<Driver>>
    fun updateDriverAvailability(driverId: String, availability: String): Flow<Result<Boolean>>
    fun observeActiveOffers(driverId: String): Flow<Result<List<Map<String, Any>>>>
    fun observeActiveOrder(driverId: String): Flow<Result<Order?>>
    fun acceptOffer(offerId: String): Flow<Result<String>>
    fun declineOffer(offerId: String): Flow<Result<Boolean>>
    fun collectOrder(orderId: String): Flow<Result<Boolean>>
    fun completeDelivery(orderId: String, otp: String): Flow<Result<Boolean>>
    fun reportIssue(orderId: String, reason: String, description: String): Flow<Result<Boolean>>
    fun getEarningsSummary(driverId: String): Flow<Result<Map<String, Any>>>
    fun getEarningsEntries(driverId: String): Flow<Result<List<Map<String, Any>>>>
    fun getDeliveryHistory(driverId: String): Flow<Result<List<Order>>>
    fun logout()
}
