package app.keeply.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IdsTest {
    @Test
    fun generatesDistinctOpaqueIdentifiers() {
        val ids = List(2_000) { Ids.generate() }
        assertEquals(2_000, ids.toSet().size)
        assertTrue(ids.all(Ids::isValid))
    }

    @Test
    fun containsNothingThatCouldLeakWhatAFileIs() {
        // Filenames in the data directory are built from these, so they must never
        // carry a merchant, a date or anything else about the receipt.
        val id = Ids.generate()
        assertTrue(id.all { it.isDigit() || it in 'a'..'z' })
        assertEquals(26, id.length)
    }

    @Test
    fun rejectsIdentifiersItDidNotGenerate() {
        assertFalse(Ids.isValid("../../etc/passwd"))
        assertFalse(Ids.isValid(""))
        assertFalse(Ids.isValid("ABCDEF"))
        assertFalse(Ids.isValid(Ids.generate() + "x"))
    }

    @Test
    fun typedIdentifiersRefuseBlankValues() {
        assertFailsWith<IllegalArgumentException> { PurchaseId("") }
        assertFailsWith<IllegalArgumentException> { DocumentId("   ") }
    }
}
