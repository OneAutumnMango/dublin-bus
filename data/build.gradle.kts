plugins {
    id("dublinbus.kotlin-library")
}

dependencies {
    api(projects.core)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okio)
    testImplementation(libs.turbine)
}
