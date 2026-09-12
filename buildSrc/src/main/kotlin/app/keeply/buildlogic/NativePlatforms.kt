package app.keeply.buildlogic

/**
 * Keeply bundles its own Tesseract and OpenCV binaries so a user never has to install
 * anything. Those binaries are per-platform and large, so the build only packages the
 * platforms it is asked for.
 *
 * Default: the machine doing the build. Override with
 * `-Pkeeply.nativePlatforms=macosx-arm64,macosx-x86_64` or `-Pkeeply.nativePlatforms=all`.
 */
public object NativePlatforms {
    public val SUPPORTED: List<String> = listOf(
        "macosx-arm64",
        "macosx-x86_64",
        "linux-x86_64",
        "linux-arm64",
        "windows-x86_64",
    )

    public fun host(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val normalisedArch = when (arch) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x86_64"
            else -> arch
        }
        return when {
            os.contains("mac") || os.contains("darwin") -> "macosx-$normalisedArch"
            os.contains("win") -> "windows-x86_64"
            else -> "linux-$normalisedArch"
        }
    }

    public fun resolve(property: String?): List<String> {
        val raw = property?.trim().orEmpty()
        if (raw.isEmpty()) return listOf(host())
        if (raw.equals("all", ignoreCase = true)) return SUPPORTED
        if (raw.equals("host", ignoreCase = true)) return listOf(host())
        val requested = raw.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
        val unknown = requested - SUPPORTED.toSet()
        require(unknown.isEmpty()) {
            "Unknown native platform(s): $unknown. Supported: $SUPPORTED"
        }
        return requested
    }
}
