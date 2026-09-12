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
    implementation(compose.materialIconsExtended)
    implementation(compose.components.resources)
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.swing)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    testImplementation("org.jetbrains.compose.ui:ui-test-junit4:${libs.versions.compose.get()}")
}

compose.desktop {
    application {
        mainClass = "app.keeply.desktop.MainKt"

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
