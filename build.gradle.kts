plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.navigation.safeargs) apply false
    // Phase 16: Secrets Gradle Plugin — keeps MAPS_API_KEY out of source control
    alias(libs.plugins.secrets.gradle) apply false
}
