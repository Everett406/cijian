plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// ── 版本号管理 ────────────────────────────────────────────────
// 本地开发：默认 1.0.0 / 1
// CI 构建：由 workflow 从 git tag 派生（v1.2.3 → versionName=1.2.3，versionCode=提交数，单调递增）
val ciVersionName: String = System.getenv("CI_VERSION_NAME") ?: "1.0.0"
val ciVersionCode: Int = System.getenv("CI_VERSION_CODE")?.toIntOrNull() ?: 1

// ── 签名配置 ─────────────────────────────────────────────────
// CI：环境变量注入 keystore 路径与密码（GitHub Secrets）
// 本地：未配置时 release 自动回落 debug 签名，保证 assembleRelease 永远可用
val keystoreFilePath = System.getenv("CI_KEYSTORE_FILE")

android {
    namespace = "com.cijian.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cijian.app"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = ciVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystoreFilePath != null) {
            create("ci") {
                storeFile = rootProject.file(keystoreFilePath)
                storePassword = System.getenv("CI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CI_KEY_ALIAS")
                keyPassword = System.getenv("CI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFilePath != null) signingConfigs.getByName("ci")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
