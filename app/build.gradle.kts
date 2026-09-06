plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.convokit.ui.example"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.convokit.ui.example"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "CONVOKIT_CLIENT_ID", "\"998da6ce-2572-42b1-8c60-734ce09c88e4\"")
        buildConfigField("String", "DEMO_BACKEND_URL", "\"https://convokit-open-chatroom.vercel.app\"")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("app.convokit:convokit-android-ui:0.2.1")
    implementation(platform("androidx.compose:compose-bom:2025.05.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
}
