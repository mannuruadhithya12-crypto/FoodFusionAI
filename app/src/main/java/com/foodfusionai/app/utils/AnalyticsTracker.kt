package com.foodfusionai.app.utils

/**
 * Generic abstraction layer for analytics tracking.
 */
interface AnalyticsTracker {
    fun logEvent(eventName: String, params: Map<String, Any>? = null)
    fun setUserProperty(name: String, value: String)
    fun setUserId(userId: String?)
}
