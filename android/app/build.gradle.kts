plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val mpayApiBaseUrl = providers.gradleProperty("mpayApiBaseUrl")
    .orElse(providers.environmentVariable("MPAY_API_BASE_URL"))
    .orElse("http://192.168.31.47:8080/")
    .map { value -> if (value.endsWith("/")) value else "$value/" }

val mapsApiKey = providers.gradleProperty("mapsApiKey")
    .orElse(providers.environmentVariable("MAPS_API_KEY"))
    .orElse("")

android {
    namespace = "com.recharge.client"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.client.mpay"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "MPAY_API_BASE_URL", "\"${mpayApiBaseUrl.get()}\"")
        buildConfigField("String", "MAPS_API_KEY", "\"${mapsApiKey.get()}\"")
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey.get()
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("MPAY_RELEASE_KEYSTORE_PATH")
            val storePassword = providers.environmentVariable("MPAY_RELEASE_STORE_PASSWORD")
            val keyAlias = providers.environmentVariable("MPAY_RELEASE_KEY_ALIAS")
            val keyPassword = providers.environmentVariable("MPAY_RELEASE_KEY_PASSWORD")

            if (keystorePath.isPresent) {
                storeFile = file(keystorePath.get())
                storePassword?.let { this.storePassword = it }
                keyAlias?.let { this.keyAlias = it }
                keyPassword?.let { this.keyPassword = it }
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.navigation:navigation-compose:2.9.3")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.google.code.gson:gson:2.13.1")

    implementation("com.razorpay:checkout:1.6.41")
    implementation("in.payu:payu-checkout-pro:3.3.14")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("com.google.maps.android:maps-compose:7.0.0")
    implementation("com.google.android.libraries.places:places:5.3.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
