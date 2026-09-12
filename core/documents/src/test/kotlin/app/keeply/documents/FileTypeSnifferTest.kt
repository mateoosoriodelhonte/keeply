package app.keeply.documents

import app.keeply.domain.DocumentFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileTypeSnifferTest {
    @Test
    fun recognisesTheThreeFormatsKeeplyAccepts() {
        assertEquals(DocumentFormat.PDF, FileTypeSniffer.detect("%PDF-1.7\n".toByteArray()))
        assertEquals(
            DocumentFormat.PNG,
            FileTypeSniffer.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)),
        )
        assertEquals(
            DocumentFormat.JPEG,
            FileTypeSniffer.detect(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())),
        )
    }

    @Test
    fun refusesEverythingElse() {
        assertNull(FileTypeSniffer.detect(byteArrayOf(0x7F, 'E'.code.toByte(), 'L'.code.toByte(), 'F'.code.toByte())))
        assertNull(FileTypeSniffer.detect("MZ".toByteArray()))
        assertNull(FileTypeSniffer.detect("#!/bin/sh\n".toByteArray()))
        assertNull(FileTypeSniffer.detect("<svg xmlns=".toByteArray()))
        assertNull(FileTypeSniffer.detect(ByteArray(0)))
        assertNull(FileTypeSniffer.detect(byteArrayOf(0x89.toByte())))
    }

    @Test
    fun spotsAnExtensionThatDisagreesWithTheContent() {
        assertTrue(FileTypeSniffer.extensionAgrees("scan.JPEG", DocumentFormat.JPEG))
        assertTrue(FileTypeSniffer.extensionAgrees("receipt.pdf", DocumentFormat.PDF))
        assertFalse(FileTypeSniffer.extensionAgrees("receipt.pdf", DocumentFormat.PNG))
        assertFalse(FileTypeSniffer.extensionAgrees("receipt", DocumentFormat.PNG))
    }
}
