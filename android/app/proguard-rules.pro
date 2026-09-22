# Optional PayU add-ons.
# Checkout Pro contains references to these integrations, but mPay does not bundle
# the optional Google Pay InApp / legacy Google credential SDKs.
-dontwarn com.google.android.apps.nbu.paisa.inapp.client.api.**
-dontwarn com.google.android.gms.auth.api.credentials.**

# Retrofit/Gson maps API DTOs reflectively at runtime. The release build is minified,
# so keep the complete DTO classes and their members intact. This is deliberately
# scoped to the API model package rather than disabling R8 for the application.
-keep class com.recharge.client.core.model.** {
    *;
}
