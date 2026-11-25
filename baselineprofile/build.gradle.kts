// Generates app/src/release/generated/baselineProfiles/{baseline,startup}-prof.txt and holds the
// startup macrobenchmark. Not part of any normal or CI build: see architecture.md §7 for when and how
// to regenerate.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.example.running.baselineprofile"
    compileSdk {
        version = release(37)
    }
    compileSdkMinor = 2

    defaultConfig {
        minSdk = 31
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // A plain AOSP image: no Google services to add noise, and the plugin can collect profiles on it
    // without root (API 33+). Never the developer's own phone or day-to-day AVDs.
    testOptions.managedDevices.localDevices {
        create("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    managedDevices += "pixel6Api34"
    useConnectedDevices = false
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
