import java.util.Properties

plugins {
    id("dublinbus.android-application")
}

android {
    namespace = "dev.dublinbustracker.app"
    defaultConfig {
        applicationId = "dev.dublinbustracker.app"

        val props = Properties()
        val localProperties = rootProject.file("local.properties")
        if (localProperties.exists()) {
            localProperties.inputStream().use { props.load(it) }
        }
        fun escaped(value: String) = value.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "SERVER_BASE_URL", "\"${escaped(props.getProperty("server.baseUrl", ""))}\"")
        buildConfigField("String", "SERVER_HEADERS", "\"${escaped(props.getProperty("server.headers", ""))}\"")
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(projects.data)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
    implementation(libs.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp)
}
