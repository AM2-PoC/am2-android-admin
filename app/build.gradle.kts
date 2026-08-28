import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val approvedSigner = providers.gradleProperty("AM2_APPROVED_SIGNER_SHA256").orElse("")

/**
 * The build's identity, supplied by CI as its run number.
 *
 * This was the literal 2 in every Admin APK ever produced. The device decides
 * an update exists by comparing version codes, so an unchanging one makes the
 * channel permanently answer "already current" -- and leaves neither end able
 * to name the build actually installed.
 *
 * A local build keeps a low number, so a developer APK can never look newer
 * than a published one and is never offered to a field device.
 */
val buildVersionCode = providers.gradleProperty("AM2_VERSION_CODE")
    .map { property ->
        val parsed = property.trim().toIntOrNull()
        require(parsed != null && parsed > 0) { "AM2_VERSION_CODE must be a positive integer" }
        parsed
    }
    .orElse(1)

/*
 * The marketing version, read from version.properties rather than written here.
 *
 * CI has to know this string to write the update manifest the panel serves, and
 * a quoted literal inside a build script is not something another job can read.
 * -PAM2_VERSION_NAME overrides it, which is how a one-off build names itself
 * without a commit.
 */
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

/*
 * The build, appended to the version name as Semantic Versioning build metadata.
 *
 * version.properties holds "1.1.0" and a human leaves it there for a release or
 * ten, so two builds of one release read identically and an operator reading a
 * version off a handset cannot say which is which.
 *
 * Semver puts exactly this after a '+': it identifies the artifact and MUST be
 * ignored when comparing versions. The alternative -- folding the build into the
 * PATCH component, 1.1.52 -- claims fifty-two backward compatible bug fixes,
 * because that is what that component means. Nothing here parses the string
 * anyway: the handset compares versionCode, and versionName is only ever shown.
 */
/*
 * Release signing material, supplied from outside the repository.
 *
 * Absent by default: a developer without the key still builds and runs. What
 * must not happen is *half* present. Hand Gradle a keystore path with no
 * password and it attaches no signing config at all, so the release artifact
 * comes out signed with the debug key -- it builds, it installs, and it is not
 * a release. Nothing in the output says otherwise.
 *
 * The check runs at configuration time, so a half-configured machine fails
 * every Gradle invocation rather than only the release task. The wrong state
 * should be loud where it is set, not discovered later in an artifact that has
 * already shipped.
 */
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

/*
 * The staging key, which is a different key on purpose.
 *
 * Android permits an install over an existing app only when the new package
 * carries the same signature. It does not care whether the key is called debug
 * or release -- a debug keystore holds a real private key. What matters is
 * continuity, and this module has never had any: every APK is built on a runner
 * that generates a debug key and discards it, so no Admin build can be
 * installed over the one before it and each round of field testing costs an
 * operator their local state.
 *
 * Separate from the release key because this one has to live in CI to be of any
 * use, and the upload key must not. Collapsing them would put the app's
 * permanent Play identity on every runner that builds a staging APK. Losing the
 * upload key is recoverable through Play; losing signature continuity for every
 * sideloaded handset is not.
 */
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
            buildConfigField("String", "BASE_URL", quotedBuildConfig(validateEndpoint("staging", "https://staging-webadmin.am2-poc.com/", "staging-webadmin.am2-poc.com")))
            buildConfigField("String", "UPDATE_APK_URL", quotedBuildConfig(validateEndpoint("staging", "https://staging-webadmin.am2-poc.com/update/admin.apk", "staging-webadmin.am2-poc.com")))
        }
        create("production") {
            dimension = "environment"
            /*
             * Build metadata on the production lane too, which a Play-listed app
             * would not do. This one is sideload-only -- the Play listing belongs
             * to the Client alone -- so there is no store page to keep tidy, and
             * every APK that reaches a handset should be able to name itself.
             */
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
        /*
         * staging is a product flavour on the *debug* build type, so
         * assembleStagingDebug signs with this one. Overriding the existing
         * debug config rather than inventing a `staging` build type: a fourth
         * build type would be one nobody assembles.
         */
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
            // Null when unconfigured, which leaves the artifact unsigned --
            // the deliberate behaviour for a developer machine. It is never the
            // debug config, because that would install and look like a release.
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