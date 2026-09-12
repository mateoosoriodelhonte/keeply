plugins {
    id("keeply.kotlin-library")
}

description = "Synthetic receipt generator used by tests and by Keeply's demo mode. Never contains real receipts."

dependencies {
    api(project(":core:domain"))
    implementation(libs.coroutines.core)
}
