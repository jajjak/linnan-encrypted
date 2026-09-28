plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    kotlin("plugin.serialization")
}

android {
    namespace = "com.linnan.encrypted"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linnan.encrypted"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        // OAuth redirect scheme shared by all provider login flows (see AndroidManifest OAuthRedirectActivity).
        manifestPlaceholders["oauthRedirectScheme"] = "com.linnan.encrypted"

        // Public OAuth client identifiers only (never a client secret). Empty by default;
        // supply real values via gradle.properties (untracked) or -P project properties
        // when you register this app with each platform's developer console.
        buildConfigField("String", "INSTAGRAM_CLIENT_ID", "\"${project.findProperty("INSTAGRAM_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "IG_BACKEND_BASE_URL", "\"${project.findProperty("IG_BACKEND_BASE_URL") ?: "https://linnan-encrypted-backend.onrender.com"}\"")
        buildConfigField("String", "GOOGLE_OAUTH_CLIENT_ID", "\"${project.findProperty("GOOGLE_OAUTH_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "TIKTOK_CLIENT_KEY", "\"${project.findProperty("TIKTOK_CLIENT_KEY") ?: ""}\"")
        buildConfigField("String", "X_CLIENT_ID", "\"${project.findProperty("X_CLIENT_ID") ?: ""}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        disable += listOf("FullBackupContent", "MissingTranslation", "ExtraTranslation")
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")

    // Browser-based OAuth (Chrome Custom Tabs) - replaces the black-screen WebView login.
    implementation("androidx.browser:browser:1.8.0")
    // Encrypted on-device token storage (no passwords are ever stored, only OAuth tokens).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    // Networking + JSON for the platform APIs.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Thumbnail previews for detected media.
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
