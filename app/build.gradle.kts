import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.baselineprofile)
}

// Load optional credentials from local.properties (or the environment) so they stay out of git.
//   MAPTILER_KEY — empty by default; the map then uses OpenFreeMap tiles, which need no key.
private val externalProps: Properties = run {
    val props = Properties()
    val localProps = rootProject.file("local.properties")
    if (localProps.exists()) localProps.inputStream().use { props.load(it) }
    props
}
private fun creds(name: String, default: String = ""): String =
    externalProps.getProperty(name) ?: System.getenv(name) ?: default

val mapTilerKey: String = creds("MAPTILER_KEY")

android {
    namespace = "com.example.running"
    compileSdk {
        version = release(37)
    }
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.example.running"
        minSdk = 31
        targetSdk = 37
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "MAPTILER_KEY", "\"$mapTilerKey\"")
    }

    signingConfigs {
        // Deliberately public: the key and both passwords are in the repo, so anyone can
        // build an APK that installs over — and updates — a GitHub release. That also means
        // anyone can sign one, so it proves nothing about who built a given APK. Never use
        // it for Play.
        create("githubPublicRelease") {
            storeFile = rootProject.file("github-public-release.jks")
            storePassword = "github-public-release"
            keyAlias = "github-public-release"
            keyPassword = "github-public-release"
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("githubPublicRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // A universal APK carries every ABI forever, but only the device's own is ever
            // loaded. MapLibre ships a native library per ABI, so dropping the x86 pair saves
            // real space on every phone. Debug builds keep every ABI, so emulators are unaffected.
            ndk {
                abiFilters += listOf("arm64-v8a")
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
    androidResources {
        localeFilters += listOf("en")
    }
    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/*.version"
            excludes += "/META-INF/com.android.tools/**"
            excludes += "/DebugProbesKt.bin"
            excludes += "/kotlin-tooling-metadata.json"
        }
    }
}

// Baseline + startup profile (architecture.md §7.1). The app is sideloaded, so it never gets Play's
// cloud profiles: the profile bundled here is the only AOT compilation it gets at install. It is
// generated on demand by :baselineprofile and committed under src/release/generated/baselineProfiles;
// ordinary and CI builds just package the committed files and need no device.
baselineProfile {
    automaticGenerationDuringBuild = false
    // Also emit startup-prof.txt; R8 uses it to put startup classes in the primary dex.
    dexLayoutOptimization = true
}

// Test hooks for the profile generator and the startup benchmark (src/benchmarkHooks: a receiver
// that skips onboarding and seeds demo runs). Only the plugin's benchmark variants get them; debug
// and the shipped release never do.
androidComponents {
    for (buildType in listOf("nonMinifiedRelease", "benchmarkRelease")) {
        onVariants(selector().withBuildType(buildType)) { variant ->
            variant.sources.kotlin?.addStaticSourceDirectory("src/benchmarkHooks/kotlin")
            variant.sources.manifests.addStaticManifestFile("src/benchmarkHooks/AndroidManifest.xml")
        }
    }
}

ksp {
    // Room writes each schema version here; MigrationTestHelper reads them from androidTest assets.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)

    implementation(libs.play.services.location)
    implementation(libs.maplibre.android)
    implementation(libs.vico.compose.m3)
    // Sideloaded installs get no profile from the installer: on first launch this copies the bundled
    // baseline profile into ART, and the next background dexopt compiles those methods.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.truth)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
