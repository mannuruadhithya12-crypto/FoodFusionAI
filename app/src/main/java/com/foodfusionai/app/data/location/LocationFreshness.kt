package com.foodfusionai.app.data.location

/**
 * Classifies a GPS timestamp into one of three freshness states.
 *
 * Used by the customer tracking UI and the admin operations map to indicate
 * whether the driver location is live, potentially stale, or offline.
 *
 * Thresholds:
 *   HEALTHY  — updated within the last 60 seconds
 *   STALE    — 60 seconds to 5 minutes
 *   OFFLINE  — more than 5 minutes (or timestamp is null)
 */
enum class LocationFreshness {
    HEALTHY,
    STALE,
    OFFLINE;

    companion object {
        private const val HEALTHY_THRESHOLD_MS  =  60_000L    // 1 minute
        private const val STALE_THRESHOLD_MS    = 300_000L    // 5 minutes

        /**
         * Classifies [lastUpdatedAt] (epoch millis) relative to [now].
         *
         * Pass [now] explicitly so this function is easy to unit-test.
         */
        fun classify(lastUpdatedAt: Long?, now: Long = System.currentTimeMillis()): LocationFreshness {
            if (lastUpdatedAt == null || lastUpdatedAt <= 0L) return OFFLINE
            val age = now - lastUpdatedAt
            return when {
                age <= HEALTHY_THRESHOLD_MS -> HEALTHY
                age <= STALE_THRESHOLD_MS   -> STALE
                else                        -> OFFLINE
            }
        }

        /**
         * Returns a human-readable "Updated X ago" string for [lastUpdatedAt].
         *
         * Examples: "Updated just now", "Updated 2 min ago", "Updated 8 min ago"
         */
        fun ageLabel(lastUpdatedAt: Long?, now: Long = System.currentTimeMillis()): String {
            if (lastUpdatedAt == null || lastUpdatedAt <= 0L) return "Location unavailable"
            val ageSeconds = (now - lastUpdatedAt) / 1000L
            return when {
                ageSeconds < 10   -> "Updated just now"
                ageSeconds < 60   -> "Updated ${ageSeconds}s ago"
                ageSeconds < 3600 -> "Updated ${ageSeconds / 60} min ago"
                else              -> "Updated ${ageSeconds / 3600}h ago"
            }
        }
    }
}
