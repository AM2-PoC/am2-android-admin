import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val approvedSigner = providers.gradleProperty("AM2_APPROVED_SIGNER_SHA256").orElse("")

fun quotedBuildConfig(value: String): String = "\"$value\""

fun validateEndpoint(environment: String, value: String, host: String): String {
    val uri = URI(value)
    require(uri.scheme == "https") { "$environment endpoint must use https" }
    require(uri.host == host) { "$environment endpoint must use $host" }
    require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "$environment endpoint must not contain userinfo, query, or fragment"
    }
    return value
}

android {
    namespace = "com.am2.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.am2.admin"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APPROVED_UPDATE_SIGNER_SHA256", "\"${approvedSigner.get()}\"")
        buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "false")
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            resValue("string", "app_name", "am² Admin DEV")
            buildConfigField("String", "BASE_URL", quotedBuildConfig(validateEndpoint("dev", "https://dev-webadmin.am2-poc.com/", "dev-webadmin.am2-poc.com")))
            buildConfigField("String", "UPDATE_APK_URL", quotedBuildConfig(validateEndpoint("dev", "https://dev-webadmin.am2-poc.com/update/admin.apk", "dev-webadmin.am2-poc.com")))
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            resValue("string", "app_name", "am² Admin STAGING")
            buildConfigField("String", "BASE_URL", quotedBuildConfig(validateEndpoint("staging", "https://staging-webadmin.am2-poc.com/", "staging-webadmin.am2-poc.com")))
            buildConfigField("String", "UPDATE_APK_URL", quotedBuildConfig(validateEndpoint("staging", "https://staging-webadmin.am2-poc.com/update/admin.apk", "staging-webadmin.am2-poc.com")))
        }
        create("production") {
            dimension = "environment"
            buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "true")
            buildConfigField("String", "BASE_URL", quotedBuildConfig(validateEndpoint("production", "https://webadmin.am2-poc.com/", "webadmin.am2-poc.com")))
            buildConfigField("String", "UPDATE_APK_URL", quotedBuildConfig(validateEndpoint("production", "https://webadmin.am2-poc.com/update/admin.apk", "webadmin.am2-poc.com")))
            val signer = approvedSigner.get().replace(":", "").lowercase()
            if (gradle.startParameter.taskNames.any { it.contains("Production", ignoreCase = true) && it.contains("Release", ignoreCase = true) }) {
                require(Regex("^[0-9a-f]{64}$").matches(signer)) {
                    "Production release requires AM2_APPROVED_SIGNER_SHA256"
                }
            }
        }
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
    implementation(libs.androidx.security.crypto)

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