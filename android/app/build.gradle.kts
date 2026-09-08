import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun prop(name: String): String =
    (localProps.getProperty(name) ?: System.getenv(name) ?: "").trim()

android {
    namespace = "com.e2s.kospiscreener"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.e2s.kospiscreener"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // 결과 JSON 이 올라가는 주소. local.properties 의 DATA_BASE_URL 로 지정하거나 앱 설정 화면에서 입력.
        // 예: https://raw.githubusercontent.com/<아이디>/<저장소>/main/docs/data
        buildConfigField("String", "DATA_BASE_URL", "\"${prop("DATA_BASE_URL")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
