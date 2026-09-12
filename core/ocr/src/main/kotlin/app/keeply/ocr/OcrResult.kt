package app.keeply.ocr

/** Where a word sits on the page, in pixels of the image that was read. */
public data class TextBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    public val width: Int get() = right - left
    public val height: Int get() = bottom - top
}

/** One word Tesseract read, and how sure it was. */
public data class OcrWord(
    val text: String,
    /** 0 to 100. Below about 60 the word is usually wrong. */
    val confidence: Float,
    val box: TextBox,
)

/**
 * What Keeply read off an image.
 *
 * Confidence travels with the text because the whole review step depends on it:
 * a total read at 42% should be shown as uncertain, not presented as a fact.
 */
public data class OcrResult(
    val text: String,
    val words: List<OcrWord>,
    val meanConfidence: Double,
    val engine: String,
    val durationMillis: Long,
) {
    public val isEmpty: Boolean get() = text.isBlank()

    /** Words Tesseract was unsure about, used by the debug view and by extraction. */
    public fun lowConfidenceWords(threshold: Float = LOW_CONFIDENCE): List<OcrWord> =
        words.filter { it.confidence < threshold && it.text.isNotBlank() }

    /**
     * True when the reading is too poor to be worth parsing.
     *
     * Presenting a review screen full of nonsense wastes a person's time more than
     * saying plainly that the photo could not be read.
     */
    public val isUnusable: Boolean
        get() = isEmpty || (meanConfidence < UNUSABLE_BELOW && text.count { it.isLetterOrDigit() } < MIN_CHARACTERS)

    public companion object {
        public const val LOW_CONFIDENCE: Float = 60f
        private const val UNUSABLE_BELOW = 45.0
        private const val MIN_CHARACTERS = 30

        public val empty: OcrResult = OcrResult("", emptyList(), 0.0, "none", 0)
    }
}
