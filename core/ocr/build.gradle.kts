import app.keeply.buildlogic.NativePlatforms

plugins {
    id("keeply.kotlin-library")
}

description = "Local Tesseract OCR with bounded concurrency, progress and cancellation. Nothing leaves the machine."

val nativePlatforms = NativePlatforms.resolve(providers.gradleProperty("keeply.nativePlatforms").orNull)

dependencies {
    api(project(":core:domain"))
    api(project(":core:imaging"))
    implementation(project(":core:documents"))
    api(libs.tesseract)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    nativePlatforms.forEach { platform ->
        runtimeOnly(variantOf(libs.tesseract) { classifier(platform) })
        runtimeOnly(variantOf(libs.leptonica) { classifier(platform) })
    }
}
