plugins {
    id("keeply.kotlin-library")
}

description = "Local reminders for closing return windows and expiring warranties. No push service."

dependencies {
    api(project(":core:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)
}
