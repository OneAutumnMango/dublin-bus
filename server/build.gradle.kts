plugins {
    id("dublinbus.kotlin-application")
}

application {
    mainClass.set("dev.dublinbustracker.server.MainKt")
}

sourceSets {
    main {
        // Reuse the same filtered static-GTFS asset the app bundles, rather than duplicating it.
        resources.srcDir(rootProject.file("data/src/main/assets"))
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.data)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
}
