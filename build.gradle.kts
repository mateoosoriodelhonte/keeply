/**
 * Keeply is a local-first desktop application. There is no server, no account
 * and no telemetry: see PRIVACY.md for the guarantees this build must uphold.
 *
 * Plugin versions live in `gradle/libs.versions.toml`; shared build conventions
 * live in `buildSrc/src/main/kotlin/keeply.kotlin-library.gradle.kts`.
 */
group = "app.keeply"
version = providers.gradleProperty("keeply.version").getOrElse("1.0.0")

tasks.register("keeplyVersion") {
    val v = version.toString()
    doLast { println(v) }
}

// --- Privacy guard -----------------------------------------------------------
//
// Keeply promises that receipts never leave the machine. This makes that promise
// checkable: the build fails if anything outside the optional local-AI module
// gains network access or an analytics dependency. See PRIVACY.md.

val privacyGuard = tasks.register<app.keeply.buildlogic.PrivacyGuardTask>("privacyGuard") {
    group = "verification"
    description = "Fails if Keeply gains network or analytics access outside :core:ai."
    // Rooted at each module's source directory rather than at the module itself, so
    // the task never has a build output folder inside its inputs.
    sources.from(
        subprojects.map { module ->
            fileTree(module.projectDir.resolve("src/main/kotlin")) { include("**/*.kt") }
        },
    )
    // The Ollama integration is opt-in, off by default, and talks only to localhost.
    allowedPaths.set(setOf("core/ai/src/"))
}

tasks.register("keeplyVerify") {
    group = "verification"
    description = "Everything CI runs: formatting, privacy guard, build and tests."
    dependsOn(privacyGuard, subprojects.map { "${it.path}:build" }, "spotlessCheck")
}
