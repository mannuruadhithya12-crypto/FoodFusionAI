package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.utils.Resource

/**
 * Interface contract for User Address management.
 */
interface AddressRepository {

    /**
     * Fetches saved delivery addresses.
     */
    suspend fun getAddresses(): Resource<List<Address>>

    /**
     * Saves a new delivery address.
     */
    suspend fun addAddress(address: Address): Resource<Unit>
}
