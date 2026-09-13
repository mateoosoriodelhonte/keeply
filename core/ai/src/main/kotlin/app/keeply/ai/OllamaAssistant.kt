package app.keeply.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.net.URI
import io.ktor.serialization.kotlinx.json.json as jsonPlugin

/** How Keeply is configured to talk to a local model, if at all. */
public data class AssistantSettings(
    /** Off unless the person switches it on. Keeply is complete without it. */
    val enabled: Boolean = false,
    /** Ollama's own default. Keeply never changes it and never looks elsewhere. */
    val endpoint: String = "http://127.0.0.1:11434",
    val model: String = "llama3.2",
    val timeoutSeconds: Long = 30,
) {
    /**
     * True when the endpoint is on this machine.
     *
     * Enforced rather than assumed: the whole point of the local-AI option is that
     * receipts do not leave the machine, and a settings file pointing somewhere else
     * would quietly break that promise.
     *
     * The host is parsed and compared exactly rather than matched as a prefix. A
     * first attempt used `startsWith`, which happily accepted
     * `http://127.0.0.1.example.invalid` — a hostname an attacker controls that
     * begins with a loopback address.
     */
    public val isLocal: Boolean
        get() = runCatching {
            val uri = URI(endpoint)
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return@runCatching false
            val host = uri.host?.lowercase()?.trim('[', ']') ?: return@runCatching false
            host in LOCAL_HOSTS
        }.getOrDefault(false)

    public companion object {
        private val LOCAL_HOSTS = setOf("127.0.0.1", "localhost", "::1", "0.0.0.0")
    }
}

/** A suggestion. Never applied on its own. */
public data class Suggestion(
    val field: String,
    val value: String,
    /** What Keeply had before, so the person can see what would change. */
    val replaces: String?,
) {
    public val isChange: Boolean get() = value.isNotBlank() && value != replaces
}

/** Whether a local model is there, and what it can do. */
public sealed interface AssistantStatus {
    public data object Disabled : AssistantStatus
    public data class Unavailable(val reason: String) : AssistantStatus
    public data class Ready(val models: List<String>) : AssistantStatus
}

/**
 * Optional help from a language model running on the same machine.
 *
 * Everything about this is deliberately at arm's length. Keeply is complete with
 * it switched off, which is the default, and nothing on the correctness path ever
 * calls it: totals, dates and return windows come from the deterministic parser
 * and nowhere else.
 *
 * What it is for is the cosmetic end of the job, where a mistake costs nothing:
 * turning `WIRELE HEADPH` into something readable, or proposing a category.
 * Every answer is a suggestion the person accepts or ignores. Nothing it returns
 * is written to a field on its own.
 *
 * Keeply never installs Ollama, never downloads a model, and never reaches
 * anywhere but the local machine.
 */
public class OllamaAssistant(private val settings: AssistantSettings, private val client: HttpClient? = null) : AutoCloseable {
    private val log = LoggerFactory.getLogger(OllamaAssistant::class.java)
    private val format = Json { ignoreUnknownKeys = true }

    private val http: HttpClient? by lazy {
        if (!settings.enabled) return@lazy null
        if (!settings.isLocal) {
            log.warn("Refusing to use an assistant endpoint that is not on this machine")
            return@lazy null
        }
        client ?: HttpClient(CIO) {
            install(ContentNegotiation) { jsonPlugin(format) }
            install(HttpTimeout) {
                requestTimeoutMillis = settings.timeoutSeconds * MILLIS
                connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
            }
        }
    }

    public val isEnabled: Boolean get() = settings.enabled && settings.isLocal

    /** Checks whether a model is actually running, without starting anything. */
    public suspend fun status(): AssistantStatus {
        if (!settings.enabled) return AssistantStatus.Disabled
        if (!settings.isLocal) {
            return AssistantStatus.Unavailable(
                "Keeply only talks to a model running on this computer, and that address is somewhere else.",
            )
        }
        val connection = http ?: return AssistantStatus.Unavailable("Local assistance is not set up.")

        return withTimeoutOrNull(settings.timeoutSeconds * MILLIS) {
            runCatching {
                val response = connection.get("${settings.endpoint}/api/tags")
                if (!response.status.isSuccess()) {
                    return@runCatching AssistantStatus.Unavailable(
                        "Ollama answered, but not with a list of models.",
                    )
                }
                val body = format.decodeFromString(TagsResponse.serializer(), response.bodyAsText())
                AssistantStatus.Ready(body.models.map { it.name })
            }.getOrElse {
                AssistantStatus.Unavailable(
                    "Keeply could not reach Ollama on this computer. Start Ollama, or leave this switched off: " +
                        "Keeply works fully without it.",
                )
            }
        } ?: AssistantStatus.Unavailable("Ollama did not answer in time.")
    }

    /**
     * Proposes a readable product name for an abbreviated till description.
     *
     * Returns null rather than guessing wildly: an answer longer than the input by
     * much, or one that looks like a sentence rather than a name, is discarded.
     */
    public suspend fun suggestProductName(tillDescription: String): Suggestion? {
        val answer = ask(
            "A shop receipt printed this abbreviated item description: \"$tillDescription\". " +
                "Write the most likely full product name. Reply with the name only, no explanation.",
        ) ?: return null

        val cleaned = answer.lines().first().trim().trim('"', '.', ' ')
        if (cleaned.isEmpty() || cleaned.length > tillDescription.length * MAX_EXPANSION) return null
        if (cleaned.count { it == ' ' } > MAX_WORDS) return null
        return Suggestion("Product", cleaned, tillDescription)
    }

    /** Proposes one of the categories Keeply already has. Never invents a new one. */
    public suspend fun suggestCategory(productName: String, categories: List<String>): Suggestion? {
        if (categories.isEmpty()) return null
        val answer = ask(
            "Which of these categories best fits a product called \"$productName\"? " +
                "Categories: ${categories.joinToString(", ")}. Reply with one category name only.",
        ) ?: return null

        // Constrained to the list rather than trusted: a model that answers with
        // something else has answered the wrong question.
        val match = categories.firstOrNull { it.equals(answer.trim(), ignoreCase = true) }
            ?: categories.firstOrNull { answer.contains(it, ignoreCase = true) }
            ?: return null
        return Suggestion("Category", match, null)
    }

    /** Summarises warranty text a person pasted in. Their words, shortened. */
    public suspend fun summariseWarranty(text: String): Suggestion? {
        if (text.length < MIN_SUMMARY_INPUT) return null
        val answer = ask(
            "Summarise this warranty in one short sentence a non-technical person would understand. " +
                "Do not add anything that is not stated.\n\n${text.take(MAX_INPUT)}",
        ) ?: return null
        return Suggestion("Warranty notes", answer.trim().take(MAX_SUMMARY), null)
    }

    private suspend fun ask(prompt: String): String? {
        val connection = http ?: return null
        return withTimeoutOrNull(settings.timeoutSeconds * MILLIS) {
            runCatching {
                val response = connection.post("${settings.endpoint}/api/generate") {
                    contentType(ContentType.Application.Json)
                    setBody(GenerateRequest(settings.model, prompt, stream = false))
                }
                if (!response.status.isSuccess()) return@runCatching null
                format.decodeFromString(GenerateResponse.serializer(), response.bodyAsText())
                    .response
                    .takeIf { it.isNotBlank() }
            }.onFailure { log.debug("Local assistant did not answer", it) }.getOrNull()
        }
    }

    override fun close() {
        runCatching { http?.close() }
    }

    @Serializable
    private data class GenerateRequest(val model: String, val prompt: String, val stream: Boolean)

    @Serializable
    private data class GenerateResponse(val response: String = "")

    @Serializable
    private data class TagsResponse(val models: List<TagModel> = emptyList())

    @Serializable
    private data class TagModel(@SerialName("name") val name: String = "")

    public companion object {
        private const val MILLIS = 1_000L
        private const val CONNECT_TIMEOUT_MILLIS = 2_000L
        private const val MAX_EXPANSION = 4
        private const val MAX_WORDS = 8
        private const val MIN_SUMMARY_INPUT = 40
        private const val MAX_INPUT = 4_000
        private const val MAX_SUMMARY = 300

        /** The default. Keeply is a complete product in this state. */
        public fun disabled(): OllamaAssistant = OllamaAssistant(AssistantSettings(enabled = false))
    }
}
