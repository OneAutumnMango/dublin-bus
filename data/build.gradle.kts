plugins {
    id("dublinbus.kotlin-library")
}

dependencies {
    api(projects.core)
    // FeedMessage is part of NtaArrivalsRepository/RemoteArrivalsRepository's public API.
    api(libs.gtfs.realtime.bindings)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.okio)
    testImplementation(libs.turbine)
}
