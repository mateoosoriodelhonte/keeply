import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

plugins {
    id("keeply.kotlin-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
}

description = "Keeply's Compose Multiplatform desktop application."

// UI code is an application surface, not a published library API.
kotlin { explicitApi = ExplicitApiMode.Disabled }

dependencies {
    implementation(project(":core:services"))
    implementation(project(":fixtures"))

    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    // ui-test rather than ui-test-junit4: runComposeUiTest works with any test
    // framework, so the interface tests run on the same JUnit Platform as the rest.
    testImplementation("org.jetbrains.compose.ui:ui-test:${libs.versions.compose.get()}")
    testImplementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "app.keeply.desktop.MainKt"

        // Development convenience: ./gradlew :desktop:run -Pkeeply.dataDir=/tmp/keeply-dev
        // runs against a throwaway library instead of the real one.
        providers.gradleProperty("keeply.dataDir").orNull?.let { jvmArgs += "-Dkeeply.dataDir=$it" }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Keeply"
            packageVersion = project.version.toString()
            description = "Know what you bought, where the receipt is, and how long you have to return it."
            vendor = "Keeply"
            licenseFile.set(rootProject.file("LICENSE"))

            // Keeply runs entirely offline; only the modules actually used are linked in.
            modules("java.sql", "java.naming", "java.management", "jdk.unsupported")

            macOS {
                bundleID = "app.keeply.desktop"
                dockName = "Keeply"
                // Set only when present so packaging works before the artwork lands.
                val icon = project.file("src/main/resources/icons/keeply.icns")
                if (icon.exists()) iconFile.set(icon)
                infoPlist {
                    extraKeysRawXml =
                        """
                        <key>NSHumanReadableCopyright</key>
                        <string>Keeply — local-first receipt keeping. No account, no cloud.</string>
                        <key>LSApplicationCategoryType</key>
                        <string>public.app-category.productivity</string>
                        """.trimIndent()
                }
            }
            windows {
                menu = true
                shortcut = true
                upgradeUuid = "6f1f1d2e-9c4a-4f15-9c2f-2a6d1f9c7b31"
            }
            linux {
                shortcut = true
                appCategory = "Office"
            }
        }
    }
}

// Renders the documentation screenshots from the real composables against demo
// data, so they cannot drift from the application. Needs no display.
tasks.register<JavaExec>("writeScreenshots") {
    group = "keeply"
    description = "Renders Keeply's screens to PNG files in build/screenshots."
    mainClass.set("app.keeply.desktop.tools.ScreenshotsKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        layout.buildDirectory
            .dir("screenshots")
            .get()
            .asFile.absolutePath,
        layout.buildDirectory
            .dir("screenshot-library")
            .get()
            .asFile.absolutePath,
    )
}

// Draws the application icon at every size macOS asks for. Run this, then
// `iconutil -c icns` to produce the .icns the packager embeds; both steps are in
// docs/LOCAL_DEVELOPMENT.md.
tasks.register<JavaExec>("writeIconset") {
    group = "keeply"
    description = "Renders the Keeply application icon into build/Keeply.iconset."
    mainClass.set("app.keeply.desktop.tools.IconArtworkKt")
    classpath = sourceSets["main"].runtimeClasspath
    args(
        layout.buildDirectory
            .dir("Keeply.iconset")
            .get()
            .asFile.absolutePath,
    )
}
