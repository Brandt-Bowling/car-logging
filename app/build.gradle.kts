import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProps = Properties().apply {
    val propFile = rootProject.file("local.properties")
    if (propFile.exists()) {
        propFile.inputStream().use { load(it) }
    }
}

fun getSecret(key: String): String =
    (localProps.getProperty(key) ?: System.getenv(key) ?: "").trim()

android {
    namespace = "com.example.car_logger"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.car_logger"
        minSdk = 26
        targetSdk = 35
        val appVersionMajor = 1
        val appVersionMinor = 1
        val buildNumber = project.findProperty("buildNumber")?.toString()?.toIntOrNull()
        versionCode = buildNumber ?: 1
        versionName = "$appVersionMajor.$appVersionMinor.${buildNumber ?: 0}"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appName"] = "Garage Journal"

        buildConfigField("String", "SMARTCAR_APPLICATION_ID", "\"${getSecret("SMARTCAR_APPLICATION_ID")}\"")
        buildConfigField("String", "SMARTCAR_CLIENT_ID", "\"${getSecret("SMARTCAR_CLIENT_ID")}\"")
        buildConfigField("String", "SMARTCAR_CLIENT_SECRET", "\"${getSecret("SMARTCAR_CLIENT_SECRET")}\"")
        buildConfigField("String", "TESLA_CLIENT_ID", "\"${getSecret("TESLA_CLIENT_ID")}\"")
        buildConfigField("String", "TESLA_CLIENT_SECRET", "\"${getSecret("TESLA_CLIENT_SECRET")}\"")
    }

    signingConfigs {
        getByName("debug") {
            val keystoreFile = file("debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appName"] = "Garage Journal (Debug)"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signs release with debug keystore to match existing Firebase releases
            signingConfig = signingConfigs.getByName("debug")
            manifestPlaceholders["appName"] = "Garage Journal"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
        lintConfig = file("lint.xml")
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "/META-INF/INDEX.LIST",
                "/META-INF/LICENSE",
                "/META-INF/LICENSE.txt",
                "/META-INF/license.txt",
                "/META-INF/NOTICE",
                "/META-INF/NOTICE.txt",
                "/META-INF/notice.txt",
                "/META-INF/ASL2.0",
                "/META-INF/*.kotlin_module"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)

    // Google Play Services Auth & Drive v3 API
    implementation(libs.play.services.auth)
    implementation(libs.google.api.client.android)
    implementation(libs.google.api.services.drive)
    implementation(libs.google.auth.library.oauth2.http)

    // Coil for Compose
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // OkHttp & Serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.androidx.work.testing)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    // Compose Lint
    lintChecks(libs.compose.lint.checks)
}
