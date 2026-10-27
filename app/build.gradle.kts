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

// A release signing value from an environment variable, or else from a Gradle property, such as one
// in ~/.gradle/gradle.properties, which every checkout and worktree reads. The environment wins even
// over -P, because Gradle doesn't say which source a property came from, and a variable has to be
// able to override the file. A blank value counts as missing, so an empty variable neither hides
// the property nor becomes a keystore path (#3).
fun releaseSigningValue(name: String): String? =
    providers.environmentVariable(name).filter { it.isNotBlank() }
        .orElse(providers.gradleProperty(name).filter { it.isNotBlank() })
        .orNull

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

    // The keystore and its password stay out of the repo (docs/toolchain.md, Release builds). The
    // release is signed only when both are found, so with the path kept in gradle.properties,
    // routine builds stay unsigned until the password is exported to make a release. A signing
    // config with a value missing would fail ./gradlew build, which builds the release too.
    // The alias is the one the doc's keytool command gives, and keytool's PKCS12 keystores use the
    // store's password for the key. The path is trimmed, since gradle.properties keeps trailing
    // spaces (#3).
    signingConfigs {
        val keystore = releaseSigningValue("BCLAUNCHER_KEYSTORE")
        val password = releaseSigningValue("BCLAUNCHER_KEYSTORE_PASSWORD")
        if (keystore != null && password != null) {
            create("release") {
                storeFile = file(keystore.trim())
                storePassword = password
                keyAlias = "bclauncher"
                keyPassword = password
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            // R8 in full mode and resource shrinking, with Android's default keep rules. App keep
            // rules go in src/main/keepRules/*.keep. The libraries bring their own (#3).
            optimization {
                enable = true
            }
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Each JaCoCo release supports class files up to some Java version, so JaCoCo is upgraded with
    // the JDK the tests run on. Without this, it's 0.8.15 with Gradle 9.8.2 and AGP 9.4.1. AGP
    // passes it to Gradle's jacoco plugin when it sets up the unit tests, replacing any
    // jacoco.toolVersion (#2).
    testCoverage {
        jacocoVersion = libs.versions.jacoco.get()
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

roborazzi {
    // Baselines are committed with the code (ARCHITECTURE.md, Tests).
    outputDir.set(file("src/test/screenshots"))
}

// The coverage check skips code no test can call directly: the components and aggregation
// classes Hilt generates, and previews, which only Android Studio runs. Only previews under ui/
// are skipped, so data-layer code such as TV preview channels can't drop out by its file name
// (ARCHITECTURE.md, Tests) (#2).
// Dagger's InstanceHolder serves a no-argument @Provides method's factory only through
// create(), for an injected Provider or Lazy. Hilt's components call the static method instead,
// so no test reaches it (#4).
val coverageExclusions = listOf(
    "**/*_HiltComponents*.class",
    "**/*_ComponentTreeDeps*.class",
    "**/*Factory\$InstanceHolder.class",
    "dagger/hilt/internal/**",
    "hilt_aggregated_deps/**",
    "**/ui/**/*PreviewsKt*.class",
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
                // Takes the file from the test task, wherever its JaCoCo extension writes it. This
                // only orders the tasks, so dependsOn is needed too.
                executionData(tasks.getByName(unitTest))
                dependsOn(unitTest)
                // JaCoCo's tasks skip themselves when there's no execution data, which would let
                // the build pass without a coverage check.
                setOnlyIf { true }
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
        // That would also instrument every class the sandbox loads, including the JDK's generated
        // reflection accessors, which breaks creating objects the way mocking libraries do (#2).
        includes = listOf("io.github.bryancassell.bclauncher.*")
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
    implementation(libs.kotlinx.coroutines.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    kspTest(libs.hilt.compiler)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)

    constraints {
        val espressoReason =
            "Compose's test rule brings Espresso 3.5.1, which calls " +
                "InputManager.getInstance() by reflection. API 37 removed that method, so " +
                "every Compose test failed until Espresso was raised to 3.7.0 (#1)."
        testImplementation(libs.androidx.test.espresso.core) { because(espressoReason) }
        androidTestImplementation(libs.androidx.test.espresso.core) { because(espressoReason) }
    }
}
