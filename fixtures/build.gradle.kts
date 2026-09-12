plugins {
    id("keeply.kotlin-library")
}

description = "Synthetic receipt generator used by tests and by Keeply's demo mode. Never contains real receipts."

dependencies {
    api(project(":core:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.pdfbox)
}

// Writes a spread of sample receipts to look at, to attach to documentation, and
// to drag onto Keeply while working on the import screens.
tasks.register<JavaExec>("writeSampleReceipts") {
    group = "keeply"
    description = "Generates sample receipt images and PDFs into build/sample-receipts."
    mainClass.set("app.keeply.fixtures.SampleWriterKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        layout.buildDirectory
            .dir("sample-receipts")
            .get()
            .asFile.absolutePath,
    )
}
