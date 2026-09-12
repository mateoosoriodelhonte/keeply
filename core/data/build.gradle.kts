plugins {
    id("keeply.kotlin-library")
    alias(libs.plugins.sqldelight)
}

description = "SQLite persistence: typed SQL, versioned migrations and FTS5 search."

sqldelight {
    databases {
        create("KeeplyDatabase") {
            packageName.set("app.keeply.data.sql")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${libs.versions.sqldelight.get()}")
            srcDirs.setFrom("src/main/sqldelight")
            schemaOutputDirectory.set(file("src/main/sqldelight/databases"))
            verifyMigrations.set(true)
            deriveSchemaFromMigrations.set(false)
        }
    }
}

dependencies {
    api(project(":core:domain"))
    api(libs.sqldelight.runtime)
    implementation(libs.sqldelight.jdbc.driver)
    implementation(libs.sqldelight.primitive.adapters)
    implementation(libs.sqlite.jdbc)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.sqlite.jdbc)
}
