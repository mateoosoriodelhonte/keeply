package app.keeply.fixtures

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Writes a set of sample receipts to a directory.
 *
 * Used to eyeball the generator, to produce images for the documentation, and to
 * have something to drag onto Keeply while working on the import screens. Run with
 * `./gradlew :fixtures:writeSampleReceipts`.
 */
public object SampleWriter {
    public fun writeTo(directory: Path, seed: Long = 20_260_912L): List<Path> {
        Files.createDirectories(directory)
        val generator = ReceiptGenerator(seed)

        return buildList {
            ReceiptLayout.entries.forEach { layout ->
                val spec = generator.spec(layout = layout)
                val stem = layout.name.lowercase()

                add(write(directory.resolve("$stem-clean.png"), ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(spec))))
                add(
                    write(
                        directory.resolve("$stem-photographed.jpg"),
                        ReceiptImageRenderer.toJpeg(ReceiptImageRenderer.render(spec, CaptureConditions.PHOTOGRAPHED)),
                    ),
                )
                add(write(directory.resolve("$stem-text.pdf"), ReceiptPdfRenderer.withTextLayer(spec)))
                add(write(directory.resolve("$stem.txt"), ReceiptTextRenderer.render(spec).toByteArray()))
            }

            val faded = generator.spec(layout = ReceiptLayout.CLASSIC_TILL)
            add(
                write(
                    directory.resolve("faded.png"),
                    ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(faded, CaptureConditions.FADED)),
                ),
            )
            add(
                write(
                    directory.resolve("blurry.png"),
                    ReceiptImageRenderer.toPng(ReceiptImageRenderer.render(faded, CaptureConditions.BLURRY)),
                ),
            )
            add(write(directory.resolve("scanned.pdf"), ReceiptPdfRenderer.asScan(faded, CaptureConditions.PHOTOGRAPHED)))
        }
    }

    private fun write(path: Path, bytes: ByteArray): Path {
        Files.write(path, bytes)
        return path
    }
}

public fun main(args: Array<String>) {
    val target = Paths.get(args.firstOrNull() ?: "build/sample-receipts")
    val written = SampleWriter.writeTo(target)
    println("Wrote ${written.size} sample receipts to ${target.toAbsolutePath()}")
}
