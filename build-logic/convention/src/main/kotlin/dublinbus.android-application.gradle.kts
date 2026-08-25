import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlinx.kover")
    id("dublinbus.detekt")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).orElseThrow()

extensions.configure<ApplicationExtension> {
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        // The release workflow passes the tag; anything else is honestly not a version.
        versionName = providers.gradleProperty("versionName").getOrElse("0.0.0-dev")
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        // Robolectric needs real resources rather than the stubbed android.jar.
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        allWarningsAsErrors = true
        progressiveMode = true
        optIn.add("kotlin.time.ExperimentalTime")
    }
}

dependencies {
    "testImplementation"(lib("kotlin-test"))
    "testImplementation"(lib("junit-jupiter"))
    "testImplementation"(lib("junit4"))
    "testImplementation"(lib("robolectric"))
    "testImplementation"(lib("androidx-test-core"))
    "testRuntimeOnly"(lib("junit-platform-launcher"))
    // Robolectric ships a JUnit4 runner only; vintage runs it next to Jupiter tests.
    "testRuntimeOnly"(lib("junit-vintage-engine"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // A repo-wide --tests filter will not match every module.
    filter.isFailOnNoMatchingTests = false
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
