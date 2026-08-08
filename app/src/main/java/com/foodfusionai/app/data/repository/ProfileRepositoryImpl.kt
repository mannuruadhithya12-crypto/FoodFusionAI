package com.foodfusionai.app.data.repository

import com.foodfusionai.app.data.models.User
import com.foodfusionai.app.utils.Resource
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ProfileRepositoryImpl(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(),
    private val cartRepository: CartRepository = CartRepositoryImpl()
) : ProfileRepository {

    override fun observeProfile(): Flow<Resource<User>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(Resource.Error("User not logged in"))
            close()
            return@callbackFlow
        }

        trySend(Resource.Loading)

        val listener = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.message ?: "Failed to load profile"))
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    if (user != null) {
                        trySend(Resource.Success(user))
                    } else {
                        trySend(Resource.Error("Profile data is invalid"))
                    }
                } else {
                    trySend(Resource.Error("Profile not found"))
                }
            }

        awaitClose { listener.remove() }
    }

    override suspend fun updateProfile(displayName: String, phoneNumber: String): Resource<Unit> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("User not logged in")
        return try {
            firestore.collection("users").document(uid)
                .update(
                    mapOf(
                        "displayName" to displayName,
                        "phoneNumber" to phoneNumber
                    )
                ).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to update profile")
        }
    }

    override suspend fun logout(): Resource<Unit> {
        return try {
            // Wipe local user-specific data to prevent leaks
            cartRepository.clearCart()
            
            // Sign out from Firebase
            auth.signOut()
            
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to log out")
        }
    }

    override suspend fun deleteAccount(): Resource<Unit> {
        return try {
            val result = functions.getHttpsCallable("deleteUserAccount").call().await()
            val data = result.data as? Map<String, Any>
            
            if (data?.get("success") == true) {
                // Ensure local cleanup as well
                cartRepository.clearCart()
                // Auth token is typically revoked by the backend function, 
                // but calling signOut ensures the local client state is updated.
                auth.signOut()
                Resource.Success(Unit)
            } else {
                Resource.Error("Failed to delete account")
            }
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Error deleting account")
        }
    }
}
