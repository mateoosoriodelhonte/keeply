package app.keeply.ocr

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Puts the Tesseract language model somewhere Tesseract can read it.
 *
 * The model ships inside the application, so OCR works offline with nothing for a
 * person to install. Tesseract wants a directory on disk rather than a stream, so
 * the file is unpacked once next to the rest of Keeply's data and reused after that.
 */
public object TessdataInstaller {
    private val log = LoggerFactory.getLogger(TessdataInstaller::class.java)

    public const val DEFAULT_LANGUAGE: String = "eng"

    private const val RESOURCE_PREFIX = "/app/keeply/ocr/tessdata"

    /**
     * Ensures the model is present under [into] and returns the directory to hand
     * Tesseract.
     *
     * That is the `tessdata` folder itself. Tesseract 5 changed this from the parent
     * folder that older documentation describes, and passing the parent fails with a
     * bare -1 and no explanation.
     */
    public fun install(into: Path, language: String = DEFAULT_LANGUAGE): Path {
        require(language.matches(LANGUAGE_PATTERN)) { "Unexpected language code '$language'" }
        val tessdata = into.resolve("tessdata")
        val target = tessdata.resolve("$language.traineddata")

        if (Files.isRegularFile(target) && Files.size(target) > 0) return tessdata

        val resource = "$RESOURCE_PREFIX/$language.traineddata"
        val stream = TessdataInstaller::class.java.getResourceAsStream(resource)
            ?: throw OcrUnavailableException(
                "Keeply could not find its text recognition data. The application may not have installed correctly.",
            )

        Files.createDirectories(tessdata)
        val partial = tessdata.resolve("$language.traineddata.part")
        stream.use { input ->
            Files.copy(input, partial, StandardCopyOption.REPLACE_EXISTING)
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
        log.info("Installed {} language data ({} bytes)", language, Files.size(target))
        return tessdata
    }

    private val LANGUAGE_PATTERN = Regex("^[a-z]{3}(_[a-z]+)?$")
}

/** Raised when OCR cannot run at all. Carries wording meant for the screen. */
public class OcrUnavailableException(public val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)
