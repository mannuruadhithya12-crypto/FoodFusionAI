# ==========================================
# FoodFusion AI - ProGuard Rules
# ==========================================

# --- Firebase ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.foodfusionai.app.data.models.** { *; }
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# --- Firestore ---
-keep class com.google.firebase.firestore.** { *; }
-keepclassmembers class com.foodfusionai.app.data.models.** {
    <init>();
    <fields>;
}

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# --- Coroutines ---
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Navigation Component ---
-keep class * extends androidx.navigation.Navigator
-keepnames class * extends android.os.Parcelable
-keepnames class * extends java.io.Serializable

# --- Coil ---
-dontwarn coil.**
-keep class coil.** { *; }

# --- Lottie ---
-dontwarn com.airbnb.lottie.**

# --- ViewBinding ---
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** bind(android.view.View);
    public static *** inflate(...);
}

# --- Kotlin ---
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# --- Enum ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Parcelable ---
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# --- R8 Full Mode compatibility ---
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
