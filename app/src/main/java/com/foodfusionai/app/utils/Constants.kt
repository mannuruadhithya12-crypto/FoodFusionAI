package com.foodfusionai.app.utils

/**
 * Application constants.
 */
object Constants {
    // Firestore Collections
    const val COLLECTION_USERS = "users"
    const val COLLECTION_RESTAURANTS = "restaurants"
    const val COLLECTION_FOODS = "foods"
    const val COLLECTION_ORDERS = "orders"
    const val COLLECTION_REVIEWS = "reviews"
    const val COLLECTION_CATEGORIES = "categories"
    const val COLLECTION_ADDRESSES = "addresses"
    const val COLLECTION_NOTIFICATIONS = "notifications"
    
    // DataStore Keys
    const val PREF_NAME = "foodfusion_prefs"
    const val KEY_IS_LOGGED_IN = "is_logged_in"
    const val KEY_USER_ID = "user_id"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_DARK_MODE = "dark_mode"
    const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
    const val KEY_FIRST_LAUNCH = "first_launch"
    
    // Intent Extras
    const val EXTRA_RESTAURANT_ID = "restaurant_id"
    const val EXTRA_FOOD_ID = "food_id"
    const val EXTRA_ORDER_ID = "order_id"
    
    // Pagination
    const val PAGE_SIZE = 20
    
    // Validation
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_NAME_LENGTH = 50
    const val PHONE_NUMBER_LENGTH = 10
}
