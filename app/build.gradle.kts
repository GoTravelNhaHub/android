import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

fun prop(name: String) = providers.gradleProperty(name).get()

// Upload key for Google Play: copy keystore.properties.example to keystore.properties (never committed).
// Without that file, release builds come out unsigned.
val keystoreFile = rootProject.file("keystore.properties")
val keystore = Properties().apply { if (keystoreFile.exists()) keystoreFile.inputStream().use(::load) }

android {
    namespace = "com.gotravelnha.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gotravelnha.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "API_BASE_URL", "\"${prop("gtnApiBaseUrl")}\"")
        buildConfigField("String", "OIDC_ISSUER", "\"${prop("gtnOidcIssuer")}\"")
        buildConfigField("String", "SITE_URL", "\"${prop("gtnSiteUrl")}\"")
        buildConfigField("String", "OIDC_CLIENT_ID", "\"gotravelnha-mobile\"")
        buildConfigField("String", "OIDC_REDIRECT_URI", "\"com.gotravelnha.app:/oauth2redirect\"")
        // Used only when the click API cannot be reached, so a booking still carries our ids
        buildConfigField("String", "KLOOK_AID", "\"89020\"")
        buildConfigField("String", "TRIP_ALLIANCE_ID", "\"3883416\"")
        buildConfigField("String", "TRIP_SID", "\"22874365\"")

        // AppAuth's RedirectUriReceiverActivity listens on this scheme (Keycloak client gotravelnha-mobile)
        manifestPlaceholders["appAuthRedirectScheme"] = "com.gotravelnha.app"
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("upload") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("upload")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    androidResources {
        generateLocaleConfig = true
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.browser)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.appauth)
    implementation(libs.osmdroid)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
