package app.keeply.ocr

import java.nio.file.Files
import java.nio.file.Path
import kotlin.math.max
import kotlin.math.min

internal object OcrTestSupport {
    /** Installed once for the whole test run: unpacking the model each time is wasteful. */
    val tessdata: Path by lazy {
        val directory = Files.createTempDirectory("keeply-tessdata")
        directory.toFile().deleteOnExit()
        TessdataInstaller.install(directory)
    }

    /**
     * How much of the expected text survived, 0 to 1, by edit distance.
     *
     * Character accuracy rather than exact match, because a single misread comma in
     * an address should not score the same as a total read wrong.
     */
    fun similarity(expected: String, actual: String): Double {
        val a = normalise(expected)
        val b = normalise(actual)
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val distance = editDistance(a, b)
        return 1.0 - distance.toDouble() / max(a.length, b.length)
    }

    /**
     * Collapses runs of whitespace and drops rule lines.
     *
     * Tesseract does not report a row of dashes as text, correctly: it is a printed
     * rule, not a word. Counting those omissions as errors made the measured accuracy
     * far worse than the reading actually was, and would have hidden real regressions
     * behind noise.
     */
    fun normalise(text: String): String = text.lineSequence()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filterNot { it.isEmpty() || isRule(it) }
        .joinToString("\n")

    private fun isRule(line: String): Boolean = line.length >= 4 && line.all { it in "-=_*~. " }

    private fun editDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = min(min(current[j - 1] + 1, previous[j] + 1), substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
