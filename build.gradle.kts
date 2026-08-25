plugins {
    alias(libs.plugins.kover)
}

dependencies {
    kover(projects.core)
    kover(projects.data)
}
