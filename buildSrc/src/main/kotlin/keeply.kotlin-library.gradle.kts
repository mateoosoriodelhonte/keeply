import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.diffplug.spotless")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

group = "app.keeply"
version = providers.gradleProperty("keeply.version").getOrElse("1.0.0")

// Keeply builds on any JDK 21 or newer and always emits Java 21 bytecode, so a
// contributor's local JDK never changes the artefact. No toolchain is downloaded.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        // Keeply favours explicit, reviewable code over clever code.
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xjdk-release=21")
        // CI builds with -Pkeeply.strict=true, so a warning fails the build there
        // while staying a warning during day-to-day development.
        allWarningsAsErrors.set(providers.gradleProperty("keeply.strict").map { it.toBoolean() }.orElse(false))
    }
    explicitApi()
}

dependencies {
    add("testImplementation", platform(libs.findLibrary("junit-bom").get()))
    add("testImplementation", libs.findLibrary("kotlin-test-junit5").get())
    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testImplementation", libs.findLibrary("coroutines-test").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
        exceptionFormat = TestExceptionFormat.FULL
        showStackTraces = true
    }
    // Keeply tests must never touch the real user data directory.
    systemProperty("keeply.testMode", "true")
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
            .editorConfigOverride(
                mapOf(
                    "ktlint_standard_function-naming" to "disabled",
                    "ktlint_standard_filename" to "disabled",
                    "max_line_length" to "140",
                ),
            )
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.findVersion("ktlint").get().requiredVersion)
    }
}
