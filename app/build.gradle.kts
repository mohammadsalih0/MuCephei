import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.mucephei.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.mucephei.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "2.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    val envStoreFile = System.getenv("SIGNING_STORE_FILE")
    val envStorePassword = System.getenv("KEY_STORE_PASSWORD")
    val envKeyAlias = System.getenv("ALIAS")
    val envKeyPassword = System.getenv("KEY_PASSWORD")

    val releaseStoreFilePath = envStoreFile ?: localProperties.getProperty("signing.store.file")
    val releaseStoreFile = releaseStoreFilePath?.let { file(it) }
    val releaseStorePassword = envStorePassword ?: localProperties.getProperty("signing.store.password")
    val releaseKeyAlias = envKeyAlias ?: localProperties.getProperty("signing.key.alias")
    val releaseKeyPassword = envKeyPassword ?: localProperties.getProperty("signing.key.password")

    val hasReleaseSigning = releaseStoreFile?.exists() == true && !releaseStorePassword.isNullOrEmpty()

    if (hasReleaseSigning) {
        signingConfigs {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }

    }
}

dependencies {
    // Core AndroidX & Kotlin
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.swiperefreshlayout)

    // Room Database
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Media3
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)

    // Palette & Coil & Gson
    implementation(libs.androidx.palette.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.gson)

    // Testing
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // AI
    implementation(libs.llamacp)
    implementation(libs.tasks.genai)
}
