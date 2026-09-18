import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val approvedSigner = providers.gradleProperty("AM2_APPROVED_SIGNER_SHA256").orElse("")

val buildVersionCode = providers.gradleProperty("AM2_VERSION_CODE")
    .map { property ->
        val parsed = property.trim().toIntOrNull()
        require(parsed != null && parsed > 0) { "AM2_VERSION_CODE must be a positive integer" }
        parsed
    }
    .orElse(1)

val buildVersionName = providers.gradleProperty("AM2_VERSION_NAME")
    .orElse(
        providers.provider {
            val file = layout.projectDirectory.file("version.properties").asFile
            require(file.isFile) { "version.properties is missing: ${file.path}" }
            val declared = Properties()
                .apply { file.inputStream().use { load(it) } }
                .getProperty("versionName")
                ?.trim()
                .orEmpty()
            require(declared.isNotEmpty()) { "version.properties declares no versionName" }
            declared
        }
    )

val signingProps: Map<String, String?> = listOf(
    "AM2_KEYSTORE_FILE",
    "AM2_KEYSTORE_PASSWORD",
    "AM2_KEY_ALIAS",
    "AM2_KEY_PASSWORD",
).associateWith { name ->
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
}
val signingConfigured = signingProps.values.all { it != null }
require(signingConfigured || signingProps.values.all { it == null }) {
    "Release signing is half configured; missing: " +
        signingProps.filterValues { it == null }.keys.joinToString(", ")
}

/* Staging uses a persistent key distinct from the production signing key. */
val stagingSigningProps: Map<String, String?> = listOf(
    "AM2_STAGING_KEYSTORE_FILE",
    "AM2_STAGING_KEYSTORE_PASSWORD",
    "AM2_STAGING_KEY_ALIAS",
    "AM2_STAGING_KEY_PASSWORD",
).associateWith { name ->
    providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
}
val stagingSigningConfigured = stagingSigningProps.values.all { it != null }
require(stagingSigningConfigured || stagingSigningProps.values.all { it == null }) {
    "Staging signing is half configured; missing: " +
        stagingSigningProps.filterValues { it == null }.keys.joinToString(", ")
}

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
        versionCode = buildVersionCode.get()
        versionName = buildVersionName.get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "APPROVED_UPDATE_SIGNER_SHA256", "\"${approvedSigner.get()}\"")
        buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "false")
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev+${buildVersionCode.get()}"
            resValue("string", "app_name", "am² Admin DEV")
            buildConfigField("String", "BASE_URL", quotedBuildConfig(validateEndpoint("dev", "https://dev-webadmin.am2-poc.com/", "dev-webadmin.am2-poc.com")))
            buildConfigField("String", "UPDATE_APK_URL", quotedBuildConfig(validateEndpoint("dev", "https://dev-webadmin.am2-poc.com/update/admin.apk", "dev-webadmin.am2-poc.com")))
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging+${buildVersionCode.get()}"
            resValue("string", "app_name", "am² Admin STAGING")
            /*
             * Staging installs its own updates, because otherwise the update
             * path is first attempted in production -- the one place a first
             * attempt should not happen. The signer it must trust is derived in
             * CI from the key that signs this lane.
             */
            buildConfigField("Boolean", "SELF_UPDATE_ENABLED", "true")
            buildConfigField("String", "BASE_URL", quotedBuildConfig(validateEndpoint("staging", "https://staging-webadmin.am2-poc.com/", "staging-webadmin.am2-poc.com")))
            buildConfigField("String", "UPDATE_APK_URL", quotedBuildConfig(validateEndpoint("staging", "https://staging-webadmin.am2-poc.com/update/admin.apk", "staging-webadmin.am2-poc.com")))
        }
        create("production") {
            dimension = "environment"

            versionNameSuffix = "+${buildVersionCode.get()}"
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

    signingConfigs {

        if (stagingSigningConfigured) {
            getByName("debug") {
                storeFile = file(stagingSigningProps.getValue("AM2_STAGING_KEYSTORE_FILE")!!)
                storePassword = stagingSigningProps.getValue("AM2_STAGING_KEYSTORE_PASSWORD")
                keyAlias = stagingSigningProps.getValue("AM2_STAGING_KEY_ALIAS")
                keyPassword = stagingSigningProps.getValue("AM2_STAGING_KEY_PASSWORD")
            }
        }
        if (signingConfigured) {
            create("release") {
                storeFile = file(signingProps.getValue("AM2_KEYSTORE_FILE")!!)
                storePassword = signingProps.getValue("AM2_KEYSTORE_PASSWORD")
                keyAlias = signingProps.getValue("AM2_KEY_ALIAS")
                keyPassword = signingProps.getValue("AM2_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {

            signingConfig = if (signingConfigured) signingConfigs.getByName("release") else null
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
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    
    implementation(libs.androidx.lifecycle.livedata.ktx)
    
    implementation(libs.mpandroidchart)
    
    implementation(libs.osmdroid)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:runner:1.6.1")
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
