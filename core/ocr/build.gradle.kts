import app.keeply.buildlogic.DownloadVerifiedFile
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

    testImplementation(project(":fixtures"))

    nativePlatforms.forEach { platform ->
        runtimeOnly(variantOf(libs.tesseract) { classifier(platform) })
        runtimeOnly(variantOf(libs.leptonica) { classifier(platform) })
    }
}

// --- Tesseract language data -------------------------------------------------
//
// Tesseract needs a trained model on disk. Keeply ships English data inside the
// application so OCR works with no network and nothing for the person to install.
// The file is fetched once at build time from a pinned tessdata_fast commit and
// verified by SHA-256 before it is allowed anywhere near the distribution.

val tessdataCommit = "87416418657359cb625c412a48b6e1d6d41c29bd"
val generatedTessdata: Provider<Directory> = layout.buildDirectory.dir("generated/tessdata")

val downloadTessdata =
    tasks.register<DownloadVerifiedFile>("downloadTessdata") {
        group = "keeply"
        description = "Downloads and verifies the English Tesseract model bundled with Keeply."
        url.set("https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/$tessdataCommit/eng.traineddata")
        sha256.set("7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2")
        outputFile.set(generatedTessdata.map { it.file("app/keeply/ocr/tessdata/eng.traineddata") })
    }

sourceSets.named("main") {
    resources.srcDir(downloadTessdata.map { generatedTessdata })
}
