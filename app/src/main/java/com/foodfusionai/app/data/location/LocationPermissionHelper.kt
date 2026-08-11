package com.foodfusionai.app.data.location

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Centralised location-permission helper for Fragments.
 *
 * Usage in a Fragment:
 *
 * ```kotlin
 * private lateinit var permissionHelper: LocationPermissionHelper
 *
 * override fun onCreate(...) {
 *     permissionHelper = LocationPermissionHelper.create(this) { state ->
 *         when (state) {
 *             is LocationPermissionState.Granted -> onLocationGranted()
 *             is LocationPermissionState.DeniedPermanently -> showSettingsPrompt()
 *             else -> showPermissionRationale()
 *         }
 *     }
 * }
 *
 * // Trigger when the user actively requests location (e.g. taps "Use my location"):
 * permissionHelper.requestLocationPermission()
 * ```
 *
 * DO NOT request permissions automatically on screen open.  Only call
 * [requestLocationPermission] in response to a deliberate user action.
 */
class LocationPermissionHelper private constructor(
    private val fragment: Fragment,
    private val onResult: (LocationPermissionState) -> Unit
) {

    private val launcher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            onResult(evaluateGrantResults(permissions))
        }

    /**
     * Checks the current permission state without asking the user.
     */
    fun currentState(): LocationPermissionState {
        val ctx = fragment.requireContext()
        return when {
            hasFineLoc(ctx) || hasCoarseLoc(ctx) -> LocationPermissionState.Granted
            isPermanentlyDenied(fragment.requireActivity()) -> LocationPermissionState.DeniedPermanently
            else -> LocationPermissionState.NotRequested
        }
    }

    /**
     * Requests ACCESS_FINE_LOCATION + ACCESS_COARSE_LOCATION from the user.
     *
     * The [onResult] callback registered at construction time will be invoked
     * with the resolved [LocationPermissionState].
     *
     * Call this ONLY in response to a deliberate user action.
     */
    fun requestLocationPermission() {
        val ctx = fragment.requireContext()
        when {
            hasFineLoc(ctx) || hasCoarseLoc(ctx) -> {
                onResult(LocationPermissionState.Granted)
            }
            isPermanentlyDenied(fragment.requireActivity()) -> {
                onResult(LocationPermissionState.DeniedPermanently)
            }
            else -> {
                launcher.launch(arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            }
        }
    }

    /**
     * Opens the app's system settings page so the user can manually grant the
     * permanently-denied permission.
     */
    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", fragment.requireContext().packageName, null)
        }
        fragment.startActivity(intent)
    }

    /**
     * Opens the device Location Settings page so the user can enable GPS.
     */
    fun openLocationSettings() {
        fragment.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun evaluateGrantResults(
        permissions: Map<String, Boolean>
    ): LocationPermissionState {
        val ctx = fragment.requireContext()
        return when {
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                LocationPermissionState.Granted

            isPermanentlyDenied(fragment.requireActivity()) ->
                LocationPermissionState.DeniedPermanently

            else -> LocationPermissionState.Denied
        }
    }

    private fun hasFineLoc(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun hasCoarseLoc(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun isPermanentlyDenied(activity: Activity): Boolean =
        !ActivityCompat.shouldShowRequestPermissionRationale(
            activity, Manifest.permission.ACCESS_FINE_LOCATION
        ) && !hasFineLoc(activity) && !hasCoarseLoc(activity)

    companion object {
        /**
         * Creates a [LocationPermissionHelper] tied to the given [fragment]'s
         * lifecycle.  Must be called during or before [Fragment.onCreate].
         */
        fun create(
            fragment: Fragment,
            onResult: (LocationPermissionState) -> Unit
        ): LocationPermissionHelper = LocationPermissionHelper(fragment, onResult)
    }
}
