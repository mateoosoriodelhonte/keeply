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
