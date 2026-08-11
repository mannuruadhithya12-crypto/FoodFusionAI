plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.navigation.safeargs)
    // Phase 16: Injects MAPS_API_KEY from local.properties into BuildConfig and AndroidManifest
    alias(libs.plugins.secrets.gradle)
}

android {
    namespace = "com.foodfusionai.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.company.foodfusionai"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // Phase 16: Secrets Gradle Plugin configuration.
    // Reads MAPS_API_KEY from local.properties (gitignored) and injects it as:
    //   • ${MAPS_API_KEY} in AndroidManifest.xml  (for the Maps meta-data tag)
    //   • BuildConfig.MAPS_API_KEY in Kotlin code  (for RoutingService / RoutingService)
    // The defaultPropertiesFileName points to secrets.defaults.properties which IS
    // committed and holds a safe placeholder so the project compiles without a real key.
}

// Secrets plugin block must be at the top-level of build.gradle.kts (outside android {})
secrets {
    // Real keys go here — file is gitignored
    propertiesFileName = "local.properties"
    // Placeholder keys committed to source — project compiles without real Maps key
    defaultPropertiesFileName = "secrets.defaults.properties"
    // Only inject properties that start with MAPS_ to avoid leaking other local props
    ignoreList.add("sdk.dir")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    
    // UI Bundle
    implementation(libs.bundles.ui)

    // Lifecycle
    implementation(libs.bundles.lifecycle)

    // Navigation
    implementation(libs.bundles.navigation)

    // Room
    implementation(libs.bundles.room)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    
    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.bundles.firebase)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.bundles.testing)

    // Razorpay
    implementation("com.razorpay:checkout:1.6.39")

    // Phase 16: Google Maps, Places, and Location
    implementation(libs.maps)
    implementation(libs.places)
    implementation(libs.play.services.location)
}
