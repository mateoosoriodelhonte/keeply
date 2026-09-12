plugins {
    id("keeply.kotlin-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

description = "Pure Kotlin domain model: purchases, money, return windows, warranties. No I/O, no framework."

dependencies {
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)
}
