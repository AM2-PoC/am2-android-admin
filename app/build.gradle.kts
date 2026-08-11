plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.am2.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.am2.admin"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    
    // Networking
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    
    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    
    // UI & Charts
    implementation(libs.mpandroidchart)
    implementation(libs.glide)
    
    // OpenStreetMap (Alternative to Google Maps)
    implementation(libs.osmdroid)
    implementation(libs.androidx.preference.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

val checkLogPolicy by tasks.registering(Exec::class) {
    group = "verification"
    description = "Fail when Android code bypasses sanitized logging"
    workingDir(rootDir)
    commandLine("python3", "scripts/check_log_policy.py")
}

tasks.named("preBuild") {
    dependsOn(checkLogPolicy)
}