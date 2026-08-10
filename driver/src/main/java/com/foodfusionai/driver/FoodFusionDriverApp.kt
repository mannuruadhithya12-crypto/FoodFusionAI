package com.foodfusionai.driver

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class FoodFusionDriverApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        try {
            FirebaseApp.initializeApp(this)
            Log.d("FoodFusionDriverApp", "Firebase initialized successfully in Driver app")
        } catch (e: Exception) {
            Log.e("FoodFusionDriverApp", "Firebase initialization failed", e)
        }
    }

    companion object {
        lateinit var instance: FoodFusionDriverApp
            private set
    }
}
