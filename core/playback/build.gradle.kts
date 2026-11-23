plugins {
    id("com.android.library")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = "com.onda.music.core.playback"
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
    implementation(project(":core:database"))
    implementation(project(":core:sources"))
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    api(libs.media3)
    api(libs.media3.session)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.ui)
    implementation(libs.media3.ffmpeg)
    implementation(libs.guava)
    implementation(libs.coroutines.guava)
    implementation(libs.concurrent.futures)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
