plugins {
    id("com.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.onda.music.core.lyrics"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:sources"))
    implementation(project(":core:database"))
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
