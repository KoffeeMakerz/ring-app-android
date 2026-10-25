import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.kinora.kinora.watch"
    compileSdk = 35

    defaultConfig {
        // Must equal the phone app's applicationId and be signed with the same key, or the Wearable
        // Data Layer won't connect the two apps. Debug gets the same ".debug" suffix as :app.
        applicationId = "com.pulseloop"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // Same key and credentials as :app (see app/build.gradle.kts).
            val localProps = Properties().apply {
                val f = rootProject.file("local.properties")
                if (f.exists()) f.inputStream().use { load(it) }
            }
            fun credential(env: String, property: String): String? =
                System.getenv(env)?.takeIf { it.isNotEmpty() } ?: localProps.getProperty(property)

            storeFile = rootProject.file("app").resolve(credential("RELEASE_STORE_FILE", "releaseStoreFile") ?: "pulseloop-release.keystore")
            storePassword = credential("RELEASE_STORE_PASSWORD", "releaseStorePassword")
            keyAlias = credential("RELEASE_KEY_ALIAS", "releaseKeyAlias") ?: "pulseloop"
            keyPassword = credential("RELEASE_KEY_PASSWORD", "releaseKeyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.wear.compose:compose-material:1.3.1")
    implementation("androidx.wear.compose:compose-foundation:1.3.1")
    implementation("androidx.core:core-ktx:1.13.1")
}
