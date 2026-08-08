package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.Address
import com.foodfusionai.app.utils.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Concrete implementation of [AddressRepository] providing initial address lists.
 */
class AddressRepositoryImpl : AddressRepository {

    private val savedAddresses = mutableListOf(
        Address(
            id = "addr_1",
            type = "Home",
            street = "123, Park Lane, Indiranagar",
            city = "Bangalore",
            state = "Karnataka",
            zipCode = "560038"
        ),
        Address(
            id = "addr_2",
            type = "Work",
            street = "Global Tech Park, Outer Ring Road",
            city = "Bangalore",
            state = "Karnataka",
            zipCode = "560103"
        )
    )

    override suspend fun getAddresses(): Resource<List<Address>> = withContext(Dispatchers.IO) {
        Resource.Success(savedAddresses.toList())
    }

    override suspend fun addAddress(address: Address): Resource<Unit> = withContext(Dispatchers.IO) {
        val newId = "addr_${savedAddresses.size + 1}"
        savedAddresses.add(address.copy(id = newId))
        Resource.Success(Unit)
    }
}
