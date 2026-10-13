plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "cl.bonvallarte.multitraductor"
    compileSdk = 35
    defaultConfig {
        applicationId = "cl.bonvallarte.multitraductor"
        minSdk = 29
        targetSdk = 34
        versionCode = 9
        versionName = "0.2.7"
    }
    ndkVersion = "28.2.13676358"
    defaultConfig {
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { cmake { arguments += "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    testImplementation("junit:junit:4.13.2")
}
