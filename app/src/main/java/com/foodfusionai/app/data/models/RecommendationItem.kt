package com.foodfusionai.app.data.models

data class RecommendationItem(
    val food: Food,
    val reason: RecommendationReason
)

enum class RecommendationReason(val text: String) {
    TRENDING("Trending today"),
    BASED_ON_PAST_ORDERS("Based on your past orders"),
    POPULAR_IN_AREA("Popular in your area")
}
