package com.foodfusionai.app.data.location

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker

/**
 * Animates a Google Maps [Marker] smoothly from its current position to a new
 * [LatLng] instead of teleporting.
 *
 * Rules for safe animation:
 *   - If the gap between old and new position is > [MAX_JUMP_KM] km, we skip
 *     animation because the driver is likely reconnecting after an offline
 *     period — animating across 30 km would look wrong.
 *   - If a previous animation is still running it is cancelled before the new
 *     one starts to avoid position accumulation.
 *   - Heading / bearing is updated on the marker concurrently if provided.
 *
 * Part V: driver heading rotation is applied here using [Marker.setRotation].
 */
object MarkerAnimator {

    private const val ANIMATION_DURATION_MS = 1_000L
    private const val MAX_JUMP_KM = 2.0

    private var activeAnimator: ValueAnimator? = null

    /**
     * Animates [marker] from its current position to [newPosition].
     *
     * @param newBearing optional heading in degrees; if non-null the marker is
     *                   rotated to face the direction of travel.
     */
    fun animateTo(
        marker: Marker,
        newPosition: LatLng,
        newBearing: Float? = null
    ) {
        val startPosition = marker.position

        // Safety: don't animate large teleports
        val startPoint = GeoPoint(startPosition.latitude, startPosition.longitude)
        val endPoint   = GeoPoint(newPosition.latitude, newPosition.longitude)
        if (DistanceCalculator.distanceKm(startPoint, endPoint) > MAX_JUMP_KM) {
            marker.position = newPosition
            newBearing?.let { marker.rotation = it }
            return
        }

        activeAnimator?.cancel()

        activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val lat = startPosition.latitude + (newPosition.latitude - startPosition.latitude) * fraction
                val lng = startPosition.longitude + (newPosition.longitude - startPosition.longitude) * fraction
                marker.position = LatLng(lat, lng)

                // Interpolate bearing rotation for smooth turning
                newBearing?.let { targetBearing ->
                    val currentRotation = marker.rotation
                    // Handle 0/360 wraparound
                    var delta = targetBearing - currentRotation
                    if (delta > 180)  delta -= 360
                    if (delta < -180) delta += 360
                    marker.rotation = currentRotation + delta * fraction
                }
            }
            start()
        }
    }

    /** Cancel any in-progress animation. Call from onDestroyView. */
    fun cancel() {
        activeAnimator?.cancel()
        activeAnimator = null
    }
}
