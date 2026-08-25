import io.gitlab.arturbosch.detekt.Detekt
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

detekt {
    parallel = true
    // The bundled defaults are a sane starting point; config/detekt/detekt.yml
    // only records where this project deliberately disagrees.
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

dependencies {
    add("detektPlugins", catalog.findLibrary("detekt-formatting").orElseThrow())
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required = true
        xml.required = false
        txt.required = false
        sarif.required = false
        md.required = false
    }
}
