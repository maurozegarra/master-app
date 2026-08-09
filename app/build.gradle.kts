plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.maurozegarra.master"
    compileSdk = 36
    compileSdkMinor = 1

    defaultConfig {
        applicationId = "com.maurozegarra.master"
        minSdk = 26
        targetSdk = 36
        // Versionado: +1 por cada APK generado. Primer APK: 1.0.1 (Fase 7).
        versionCode = 141
        versionName = "1.0.141"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Firmamos el release con la clave debug para conservar una firma estable
            // (permite actualizar encima sin desinstalar).
            signingConfig = signingConfigs.getByName("debug")
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
    buildFeatures {
        compose = true
    }

    configurations.all {
        resolutionStrategy.force(
            "androidx.compose.foundation:foundation:1.6.8",
            "androidx.compose.foundation:foundation-android:1.6.8",
            "androidx.compose.foundation:foundation-layout:1.6.8",
            "androidx.compose.foundation:foundation-layout-android:1.6.8",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("io.insert-koin:koin-android:4.2.0")
    implementation("io.insert-koin:koin-androidx-compose:4.2.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
