plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.serialization.gradlePlugin)
    implementation(libs.kover.gradlePlugin)
    implementation(libs.android.gradlePlugin)
    implementation(libs.compose.compiler.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
}
