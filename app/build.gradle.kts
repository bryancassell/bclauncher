import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ScopedArtifacts

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
    jacoco
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Tests run on the JDK that runs Gradle, pinned in gradle/gradle-daemon-jvm.properties,
                // because Robolectric 4.17 needs Java 21 or later for SDK 36 and 37.
                // Robolectric runs API 37's framework code, whose ApplicationSharedMemory reaches
                // FileDescriptor internals through jdk.internal.access. JDK 17 and later block that
                // unless it's opened (#1).
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }
}

jacoco {
    // Each JaCoCo release supports class files up to some Java version, so the agent is upgraded
    // with the JDK the tests run on, not left at Gradle's default (#2).
    toolVersion = libs.versions.jacoco.get()
}

roborazzi {
    // Baselines are committed with the code (ARCHITECTURE.md, Tests).
    outputDir.set(file("src/test/screenshots"))
}

// The coverage check skips code no test can call directly: classes that Hilt generates, and
// previews, which only Android Studio runs (ARCHITECTURE.md, Tests) (#2).
val coverageExclusions = listOf(
    "**/Hilt_*.class",
    "**/*_Hilt*.class",
    "**/*_ComponentTreeDeps*.class",
    "**/*_GeneratedInjector*.class",
    "**/*_Factory*.class",
    "**/*_MembersInjector*.class",
    "dagger/hilt/internal/**",
    "hilt_aggregated_deps/**",
    "**/*PreviewsKt*.class",
    "**/ComposableSingletons$*PreviewsKt*.class",
)

// AGP's own unit test coverage report can't skip classes or fail the build, so these tasks read
// its execution data. They analyze the variant's final classes, after Hilt rewrites them, which
// are the classes the tests ran (#2).
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        val variantName = variant.name.replaceFirstChar(Char::titlecase)
        val unitTest = "test${variantName}UnitTest"

        val report = tasks.register<JacocoReport>("jacoco${variantName}CoverageReport") {
            reports {
                html.required = true
                xml.required = true
            }
        }
        val verification = tasks.register<JacocoCoverageVerification>(
            "jacoco${variantName}CoverageVerification",
        ) {
            violationRules {
                rule {
                    element = "CLASS"
                    limit {
                        counter = "LINE"
                        minimum = "0.80".toBigDecimal()
                    }
                }
            }
        }

        // Locals, because the configuration cache can't store a lambda that refers to the script.
        val objectFactory = objects
        val exclusions = coverageExclusions
        listOf(report, verification).forEach { task ->
            val jars = objectFactory.listProperty<RegularFile>()
            val dirs = objectFactory.listProperty<Directory>()
            task.configure {
                dependsOn(unitTest)
                executionData.setFrom(
                    layout.buildDirectory.file(
                        "outputs/unit_test_code_coverage/${variant.name}UnitTest/$unitTest.exec",
                    ),
                )
                sourceDirectories.setFrom("src/main/kotlin")
                // The jars hold only R classes, which have no lines to cover.
                classDirectories.setFrom(
                    jars,
                    dirs.map {
                        it.map { dir -> objectFactory.fileTree().from(dir).exclude(exclusions) }
                    },
                )
            }
            variant.artifacts
                .forScope(ScopedArtifacts.Scope.PROJECT)
                .use(task)
                .toGet(ScopedArtifact.CLASSES, { jars }, { dirs })
        }

        tasks.named("check") { dependsOn(verification) }
    }
}

tasks.withType<Test>().configureEach {
    extensions.configure<JacocoTaskExtension> {
        // Robolectric loads the app's classes through its own class loader, which gives them no
        // code source. Without this, JaCoCo skips them and every class reads 0% (#2).
        isIncludeNoLocationClasses = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
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
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    constraints {
        testImplementation(libs.androidx.test.espresso.core) {
            because(
                "Compose's test rule brings Espresso 3.5.1, which calls " +
                    "InputManager.getInstance() by reflection. API 37 removed that method, so " +
                    "every Compose test failed until Espresso was raised to 3.7.0 (#1).",
            )
        }
    }
}
