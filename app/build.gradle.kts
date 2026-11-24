import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

extensions.configure<ApplicationExtension> {
    namespace = "com.onda.music"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.onda.music"
        minSdk = 29
        targetSdk = 35
        versionCode = 11
        versionName = "1.2.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "abi"
    productFlavors {
        create("arm64") {
            dimension = "abi"
            ndk {
                abiFilters += "arm64-v8a"
            }
        }
        create("universal") {
            dimension = "abi"
            ndk {
                abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
            }
        }
    }

    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("ONDA_KEYSTORE_PATH")
            val keystorePassword = System.getenv("ONDA_KEYSTORE_PASSWORD")
            val keyAliasValue = System.getenv("ONDA_KEY_ALIAS")
            val keyPasswordValue = System.getenv("ONDA_KEY_PASSWORD")
            if (!keystorePath.isNullOrBlank() &&
                !keystorePassword.isNullOrBlank() &&
                !keyAliasValue.isNullOrBlank() &&
                !keyPasswordValue.isNullOrBlank() &&
                file(keystorePath).exists()
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            val releaseSigning = signingConfigs.getByName("release")
            signingConfig = if (releaseSigning.storeFile != null) {
                releaseSigning
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += listOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/NOTICE.md",
                "META-INF/CONTRIBUTORS.md",
                "META-INF/LICENSE.md",
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:design"))
    implementation(project(":core:database"))
    implementation(project(":core:sources"))
    implementation(project(":core:lyrics"))
    implementation(project(":core:transfer"))
    implementation(project(":core:playback"))
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:library"))
    implementation(project(":feature:player"))
    implementation(project(":feature:settings"))

    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.core.splashscreen)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.compose.animation)
    implementation(libs.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.coil)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.work.runtime.ktx)

    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
}
