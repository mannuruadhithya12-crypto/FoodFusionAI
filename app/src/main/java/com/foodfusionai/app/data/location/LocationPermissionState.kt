package com.foodfusionai.app.data.location

/**
 * All possible states for the device location permission lifecycle.
 *
 * UI code should switch on this sealed class rather than checking multiple
 * boolean flags.
 */
sealed class LocationPermissionState {

    /** Permission has been granted; GPS may be used. */
    object Granted : LocationPermissionState()

    /**
     * Permission was denied once but the user can still be asked again.
     * Show a rationale dialog explaining why location is needed.
     */
    object Denied : LocationPermissionState()

    /**
     * Permission was permanently denied ("Don't ask again" was selected).
     * Show a "Open Settings" prompt.
     */
    object DeniedPermanently : LocationPermissionState()

    /**
     * Permission is technically granted but the device GPS / location
     * service is turned off.  Show "Enable Location" prompt.
     */
    object LocationDisabled : LocationPermissionState()

    /** Initial state — has not been asked yet. */
    object NotRequested : LocationPermissionState()
}
