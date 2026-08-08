package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for User Address management.
 */
interface AddressRepository {

    /**
     * Observes real-time changes to the user's addresses.
     */
    fun observeAddresses(): kotlinx.coroutines.flow.Flow<Resource<List<Address>>>

    /**
     * Saves a new delivery address.
     */
    suspend fun addAddress(address: Address): Resource<Unit>
    
    /**
     * Updates an existing delivery address.
     */
    suspend fun updateAddress(address: Address): Resource<Unit>
    
    /**
     * Deletes a delivery address.
     */
    suspend fun deleteAddress(addressId: String): Resource<Unit>
    
    /**
     * Sets a specific address as default via callable backend function.
     */
    suspend fun setDefaultAddress(addressId: String): Resource<Unit>
}
