import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * The shared bridge key the official bridge's `check_auth()` requires on its
 * legacy yt-dlp-backed routes. Injected at build time -- from a gitignored
 * `secrets.properties` locally, or `-PlumiBridgeApiKey=...` / the
 * `LUMI_BRIDGE_API_KEY` environment variable in CI -- and never committed,
 * mirroring how Lumisound reads it out of `Config/Secrets.xcconfig`. An
 * empty value just means no key is sent, exactly like pointing the app at a
 * self-hosted bridge with none configured.
 */
val bridgeApiKey: String = run {
    val fromProps = rootProject.file("secrets.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }.getProperty("lumiBridgeApiKey")
    }
    (findProperty("lumiBridgeApiKey") as String?)
        ?: fromProps
        ?: System.getenv("LUMI_BRIDGE_API_KEY")
        ?: ""
}

android {
    namespace = "com.lumisound.android"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumisound.android"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "DEFAULT_BRIDGE_URL", "\"https://lumisound-bridge.xenusanimations.studio\"")
        buildConfigField("String", "BRIDGE_API_KEY", "\"$bridgeApiKey\"")
    }

    signingConfigs {
        // Release signing is opt-in: a `keystore.properties` (gitignored) or the
        // matching env vars in CI. Without them a release build stays unsigned
        // rather than failing the whole build.
        create("release") {
            val props = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
                Properties().apply { file.inputStream().use { load(it) } }
            }
            val storePath = props?.getProperty("storeFile") ?: System.getenv("KEYSTORE_PATH")
            if (storePath != null && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = props?.getProperty("storePassword") ?: System.getenv("KEYSTORE_PASSWORD")
                keyAlias = props?.getProperty("keyAlias") ?: System.getenv("KEY_ALIAS")
                keyPassword = props?.getProperty("keyPassword") ?: System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.getByName("release").storeFile?.let { signingConfig = signingConfigs.getByName("release") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime)
    implementation(libs.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
}
