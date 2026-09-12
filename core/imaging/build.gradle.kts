import app.keeply.buildlogic.NativePlatforms

plugins {
    id("keeply.kotlin-library")
}

description = "OpenCV receipt preprocessing: edge detection, perspective correction, contrast, thumbnails."

// Native binaries are large, so only the requested platforms are packaged.
// See buildSrc/src/main/kotlin/app/keeply/buildlogic/NativePlatforms.kt
val nativePlatforms = NativePlatforms.resolve(providers.gradleProperty("keeply.nativePlatforms").orNull)

dependencies {
    api(project(":core:domain"))
    api(libs.javacpp)
    api(libs.opencv)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    nativePlatforms.forEach { platform ->
        runtimeOnly(variantOf(libs.javacpp) { classifier(platform) })
        runtimeOnly(variantOf(libs.opencv) { classifier(platform) })
        runtimeOnly(variantOf(libs.openblas) { classifier(platform) })
    }
}
