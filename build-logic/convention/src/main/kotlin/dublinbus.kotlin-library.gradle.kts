import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("org.jetbrains.kotlinx.kover")
    id("dublinbus.detekt")
}

// Precompiled script plugins get no generated `libs` accessors, so resolve the catalog directly.
val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun lib(alias: String) = catalog.findLibrary(alias).orElseThrow()

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
    "testRuntimeOnly"(lib("junit-platform-launcher"))
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
