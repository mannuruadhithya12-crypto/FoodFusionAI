package com.foodfusionai.app

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

/**
 * Application class for FoodFusion AI.
 * Initializes core components: Firebase, logging, and global state.
 */
class FoodFusionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        Log.d(TAG, "Firebase initialized successfully")

        Log.d(TAG, "Application created")
    }

    companion object {
        private const val TAG = "FoodFusionApp"

        @SuppressLint("StaticFieldLeak") // Application context does not cause memory leak
        lateinit var instance: FoodFusionApp
            private set
    }
}
