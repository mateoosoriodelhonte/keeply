package app.keeply.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build if Keeply's core or interface gains the ability to talk to the
 * network, or picks up an analytics dependency.
 *
 * Keeply's central promise is that receipts stay on the machine. That promise is
 * easy to make in a README and easy to break by accident in a pull request, so it is
 * enforced here instead of being left to good intentions.
 *
 * The one module allowed to open a connection is the optional local AI integration,
 * which talks to Ollama on localhost and is switched off by default.
 */
public abstract class PrivacyGuardTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val sources: ConfigurableFileCollection

    @get:Internal
    public abstract val allowedPaths: SetProperty<String>

    @TaskAction
    public fun check() {
        val allowed = allowedPaths.get()
        val violations = mutableListOf<String>()
        var scanned = 0

        sources.files.asSequence()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val path = file.invariantSeparatorsPath
                if (allowed.any { path.contains(it) }) return@forEach
                scanned++
                file.readLines().forEachIndexed { index, line ->
                    val code = line.substringBefore("//").trim()
                    if (code.isEmpty()) return@forEachIndexed
                    FORBIDDEN.forEach { (pattern, reason) ->
                        if (pattern.containsMatchIn(code)) {
                            violations += "${file.name}:${index + 1}  $reason\n      $code"
                        }
                    }
                }
            }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Keeply's privacy guard rejected ${violations.size} line(s).")
                    appendLine()
                    appendLine("Receipts stay on the user's machine. Only :core:ai may open a connection,")
                    appendLine("and only to a local Ollama the person chose to run.")
                    appendLine()
                    violations.forEach { appendLine("  $it") }
                },
            )
        }
        logger.lifecycle("Privacy guard: $scanned files clean, no network or analytics access outside :core:ai.")
    }

    private companion object {
        val FORBIDDEN: List<Pair<Regex, String>> = listOf(
            Regex("""^import\s+java\.net\.(URL|URI|Socket|HttpURLConnection|http\.)""") to
                "opens a network connection",
            Regex("""^import\s+javax\.net\.""") to "opens a network connection",
            Regex("""^import\s+io\.ktor\.client""") to "uses an HTTP client",
            Regex("""^import\s+okhttp3""") to "uses an HTTP client",
            Regex("""^import\s+retrofit2""") to "uses an HTTP client",
            Regex("""\bHttpClient\s*\(""") to "constructs an HTTP client",
            Regex("""\bSocket\s*\(""") to "opens a socket",
            Regex("""(?i)\b(firebase|mixpanel|amplitude|segment|sentry|googleanalytics|appcenter)\b""") to
                "references an analytics or telemetry SDK",
        )
    }
}
