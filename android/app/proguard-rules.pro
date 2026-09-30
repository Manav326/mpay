# Optional PayU add-ons.
# Checkout Pro contains references to these integrations, but mPay does not bundle
# the optional Google Pay InApp / legacy Google credential SDKs.
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.**
-dontwarn com.google.android.gms.auth.api.credentials.**

# Retrofit/Gson maps API DTOs reflectively at runtime. The release build is minified,
# so keep the complete DTO classes and their members intact. This is deliberately
# scoped to the API model package rather than disabling R8 for the application.
# This protects both authentication responses and subsequent authenticated API DTOs.
-keep class com.recharge.client.core.model.** {
    *;
}

# WebRTC JNI bindings are discovered/registered from native JNI code and therefore
# are not reliably visible to R8's reachability analysis. Keep these bindings
# intact in the minified release build so libjingle_peerconnection_so can complete
# JNI_OnLoad without aborting the Android process.
-keep class org.jni_zero.** {
    *;
}

# JniZeroJni is generated/implemented on the native side and is not present as
# a Java/Kotlin class for R8 to resolve at build time.
-dontwarn org.jni_zero.JniZeroJni

-keep class org.webrtc.** {
    *;
}

# Keep the JNI Zero bootstrap explicitly as a safeguard for native registration.
-keep class org.jni_zero.JniInit {
    private static java.lang.Object[] init();
}
