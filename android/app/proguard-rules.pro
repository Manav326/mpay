# Optional PayU add-ons.
# Checkout Pro contains references to these integrations, but mPay does not bundle
# the optional Google Pay InApp / legacy Google credential SDKs.
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.**
-dontwarn com.google.android.gms.auth.api.credentials.**

# Retrofit/Gson serializes the API DTOs reflectively. Release R8 must preserve
# their backing field names; otherwise Gson can see obfuscated names instead of
# the JSON property names expected by the backend.
-keepclassmembers class com.recharge.client.core.model.** {
    <fields>;
}
