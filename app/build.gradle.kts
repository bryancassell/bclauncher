plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "io.github.bryancassell.bclauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.bryancassell.bclauncher"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric 4.17 runs API 37's framework code, whose ApplicationSharedMemory reaches
                // FileDescriptor internals through jdk.internal.access. JDK 25 blocks that unless it's opened.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

roborazzi {
    // Baselines live in the repo so a screenshot change shows up in the PR's diff.
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    // Compose's test rule brings Espresso 3.5.1, which calls InputManager.getInstance() by reflection.
    // API 37 removed that method, so every Compose test failed until Espresso was raised to 3.7.0.
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
