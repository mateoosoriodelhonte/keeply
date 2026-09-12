plugins {
    id("keeply.kotlin-library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

description = "Portable backup archives plus CSV/JSON export. Hardened against malicious archives."

dependencies {
    api(project(":core:domain"))
    api(project(":core:data"))
    api(project(":core:documents"))
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":fixtures"))
}
