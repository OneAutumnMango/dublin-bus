plugins {
    id("dublinbus.kotlin-library")
}

dependencies {
    api(projects.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.gtfs.realtime.bindings)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okio)
    testImplementation(libs.turbine)
}
