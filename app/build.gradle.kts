import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val supabaseProperties = Properties().apply {
    val propertiesFile = rootProject.file("supabase.properties")

    check(propertiesFile.exists()) {
        "Falta el archivo supabase.properties"
    }

    propertiesFile.inputStream().use { load(it) }
}

val supabaseUrl = supabaseProperties
    .getProperty("SUPABASE_URL")
    ?.takeIf { it.isNotBlank() }
    ?: error("Falta SUPABASE_URL")

val supabasePublicKey = supabaseProperties
    .getProperty("SUPABASE_PUBLIC_KEY")
    ?.takeIf { it.isNotBlank() }
    ?: error("Falta SUPABASE_PUBLIC_KEY")

android {
    namespace = "com.upupprogress.app"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.upupprogress.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"$supabaseUrl\""
        )

        buildConfigField(
            "String",
            "SUPABASE_PUBLIC_KEY",
            "\"$supabasePublicKey\""
        )
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}