plugins {
    id("keeply.kotlin-library")
}

description = "Application services wiring the import pipeline, library, search, reminders and backups together."

dependencies {
    api(project(":core:domain"))
    api(project(":core:data"))
    api(project(":core:documents"))
    api(project(":core:imaging"))
    api(project(":core:ocr"))
    api(project(":core:extraction"))
    api(project(":core:backup"))
    api(project(":core:reminders"))
    api(project(":core:ai"))
    // Demo mode generates its receipts with the same generator the tests use.
    api(project(":fixtures"))
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)
}
