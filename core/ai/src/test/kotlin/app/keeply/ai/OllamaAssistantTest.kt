package app.keeply.ai

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The optional local assistant, checked for the properties that make it safe to
 * offer at all: off by default, local only, and never authoritative.
 */
class OllamaAssistantTest {

    @Test
    fun isOffUntilSomebodyTurnsItOn() {
        // Keeply is a complete product in this state, and this is the state it ships in.
        val assistant = OllamaAssistant.disabled()
        assertFalse(assistant.isEnabled)
        assertEquals(AssistantStatus.Disabled, runBlocking { assistant.status() })
    }

    @Test
    fun onlyEverTalksToThisMachine() {
        // The entire point of local AI is that receipts do not leave the machine.
        // A settings file pointing elsewhere would break that quietly, so it is
        // checked rather than assumed.
        assertTrue(AssistantSettings(endpoint = "http://127.0.0.1:11434").isLocal)
        assertTrue(AssistantSettings(endpoint = "http://localhost:11434").isLocal)
        assertFalse(AssistantSettings(endpoint = "https://api.example.invalid").isLocal)
        assertFalse(AssistantSettings(endpoint = "http://192.168.1.50:11434").isLocal)
        // A hostname an attacker controls that begins with a loopback address. The
        // first version of this check compared prefixes and let it through.
        assertFalse(AssistantSettings(endpoint = "http://127.0.0.1.example.invalid").isLocal)
        assertFalse(AssistantSettings(endpoint = "http://localhost.example.invalid").isLocal)
        assertFalse(AssistantSettings(endpoint = "http://user@evil.invalid/?h=127.0.0.1").isLocal)
        assertFalse(AssistantSettings(endpoint = "file:///etc/passwd").isLocal)
        assertFalse(AssistantSettings(endpoint = "not a url at all").isLocal)
        assertTrue(AssistantSettings(endpoint = "http://[::1]:11434").isLocal)
    }

    @Test
    fun refusesToRunAgainstARemoteEndpointEvenWhenEnabled() = runBlocking {
        val assistant = OllamaAssistant(
            AssistantSettings(enabled = true, endpoint = "https://api.example.invalid"),
        )
        assertFalse(assistant.isEnabled)
        val status = assistant.status()
        assertTrue(status is AssistantStatus.Unavailable)
        assertContains(status.reason, "on this computer")
    }

    @Test
    fun saysPlainlyWhenOllamaIsNotRunning() = runBlocking {
        // An unused port on this machine: nothing is there to answer.
        val assistant = OllamaAssistant(
            AssistantSettings(enabled = true, endpoint = "http://127.0.0.1:1", timeoutSeconds = 2),
        )
        val status = assistant.status()
        assertTrue(status is AssistantStatus.Unavailable, "expected unavailable, got $status")
        assertContains(status.reason, "works fully without it")
        assistant.close()
    }

    @Test
    fun aSuggestionKnowsWhatItWouldReplace() {
        // The review screen shows both, so a person can see what would change
        // before anything does.
        val suggestion = Suggestion("Product", "Wireless Headphones", "WIRELE HEADPH")
        assertTrue(suggestion.isChange)
        assertEquals("WIRELE HEADPH", suggestion.replaces)

        assertFalse(Suggestion("Product", "Same", "Same").isChange)
        assertFalse(Suggestion("Product", "", "Something").isChange)
    }

    @Test
    fun nothingIsReturnedWhenTheAssistantIsOff() = runBlocking {
        val assistant = OllamaAssistant.disabled()
        assertEquals(null, assistant.suggestProductName("WIRELE HEADPH"))
        assertEquals(null, assistant.suggestCategory("Headphones", listOf("Electronics")))
        assertEquals(null, assistant.summariseWarranty("Two year manufacturer warranty covering parts and labour."))
    }
}
