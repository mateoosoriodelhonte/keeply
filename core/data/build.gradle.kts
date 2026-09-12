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
    api(libs.sqldelight.sqlite.driver)
    implementation(libs.sqldelight.primitive.adapters)
    api(libs.sqldelight.coroutines)
    implementation(libs.sqlite.jdbc)
    implementation(libs.coroutines.core)
    implementation(libs.slf4j.api)

    testImplementation(libs.sqlite.jdbc)
}

// The schema snapshot lives beside the .sq files so SQLDelight's migration
// verification can find it, which means one generator writes into the other's
// input directory. Ordering them explicitly keeps Gradle's validation happy.
// SQLDelight registers its tasks late, so they are matched by name rather than
// looked up eagerly.
tasks.matching { it.name == "generateMainKeeplyDatabaseInterface" }.configureEach {
    mustRunAfter("generateMainKeeplyDatabaseSchema")
}
