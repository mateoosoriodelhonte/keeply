plugins {
    id("keeply.kotlin-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

description = "Optional, opt-in local Ollama assistance. Disabled by default; never on the correctness path."

dependencies {
    api(project(":core:domain"))
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)
}
