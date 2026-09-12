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
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":fixtures"))
}
