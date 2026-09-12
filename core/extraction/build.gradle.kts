plugins {
    id("keeply.kotlin-library")
}

description = "Deterministic receipt field extraction: money, dates, merchants, totals. No AI required."

dependencies {
    api(project(":core:domain"))
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)
}
