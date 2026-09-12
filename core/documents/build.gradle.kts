plugins {
    id("keeply.kotlin-library")
}

description = "Safe import of untrusted receipts: type sniffing, content-addressed storage, PDF text extraction."

dependencies {
    api(project(":core:domain"))
    api(libs.pdfbox)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":fixtures"))
}
