package app.keeply.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

/**
 * Downloads a file and refuses to produce it unless the bytes match an expected
 * SHA-256. Keeply uses this for Tesseract language data, which is the one runtime
 * asset the build fetches from the network.
 *
 * Pinning the digest means a compromised or silently-updated upstream cannot change
 * what ends up inside the shipped application.
 */
@CacheableTask
public abstract class DownloadVerifiedFile : DefaultTask() {
    @get:Input
    public abstract val url: Property<String>

    @get:Input
    public abstract val sha256: Property<String>

    @get:OutputFile
    public abstract val outputFile: RegularFileProperty

    @TaskAction
    public fun run() {
        val target = outputFile.get().asFile
        val expected = sha256.get().lowercase()

        if (target.isFile && digestOf(target.readBytes()) == expected) {
            logger.lifecycle("Verified cached ${target.name}")
            return
        }

        target.parentFile.mkdirs()
        val bytes = download(url.get())
        val actual = digestOf(bytes)
        if (actual != expected) {
            throw GradleException(
                "Checksum mismatch for ${url.get()}\n  expected SHA-256 $expected\n  actual   SHA-256 $actual\n" +
                    "Refusing to write ${target.name}.",
            )
        }
        target.writeBytes(bytes)
        logger.lifecycle("Downloaded and verified ${target.name} (${bytes.size} bytes)")
    }

    private fun download(from: String): ByteArray {
        var remaining = MAX_REDIRECTS
        var current = from
        while (true) {
            val connection = URI(current).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", "keeply-build")
            try {
                when (val code = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> return connection.inputStream.use { it.readBytes() }
                    HttpURLConnection.HTTP_MOVED_PERM,
                    HttpURLConnection.HTTP_MOVED_TEMP,
                    HttpURLConnection.HTTP_SEE_OTHER,
                    TEMPORARY_REDIRECT,
                    PERMANENT_REDIRECT,
                    -> {
                        val location = connection.getHeaderField("Location")
                            ?: throw IOException("Redirect from $current without a Location header")
                        if (remaining-- <= 0) throw IOException("Too many redirects starting at $from")
                        current = URI(current).resolve(location).toString()
                    }
                    else -> throw IOException("HTTP $code fetching $current")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun digestOf(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TIMEOUT_MS = 60_000
        const val MAX_REDIRECTS = 5
        const val TEMPORARY_REDIRECT = 307
        const val PERMANENT_REDIRECT = 308
    }
}
