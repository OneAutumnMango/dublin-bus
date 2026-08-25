package dev.dublinbustracker.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HttpHeader(val name: String, val value: String)

/**
 * Where the arrivals server lives and what to send with each request. Headers are free-form so
 * any auth scheme works: the server's own `X-Api-Key`, a bearer token, or extra headers a
 * reverse proxy in front of it requires.
 */
@Serializable
data class ServerSettings(
    val baseUrl: String,
    val headers: List<HttpHeader> = emptyList(),
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()

    /** Blank rows are dropped so a half-filled form row never breaks a request. */
    fun headerMap(): Map<String, String> =
        headers.filter { it.name.isNotBlank() }.associate { it.name.trim() to it.value.trim() }

    fun toJson(): String = json.encodeToString(this)

    /** Human-readable problems to show in the settings form; empty means good to save. */
    fun validate(): List<String> {
        val problems = mutableListOf<String>()
        if (baseUrl.isBlank()) {
            problems += "Server address is required"
        } else if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            problems += "Server address must start with http:// or https://"
        }
        headers.filter { it.name.isNotBlank() || it.value.isNotBlank() }.forEach { header ->
            if (header.name.isBlank()) {
                problems += "A header value was given without a name"
            } else if (!header.name.trim().all { it.isLetterOrDigit() || it in "-_" }) {
                problems += "Header name '${header.name}' has invalid characters"
            }
        }
        return problems
    }

    companion object {
        val EMPTY = ServerSettings(baseUrl = "", headers = emptyList())

        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(raw: String): ServerSettings? = runCatching { json.decodeFromString<ServerSettings>(raw) }.getOrNull()

        /** Parses the `Name=Value,Name2=Value2` form used to seed defaults at build time. */
        fun parseHeaderSpec(raw: String): List<HttpHeader> =
            raw.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.contains("=") }
                .map { pair ->
                    val (name, value) = pair.split("=", limit = 2)
                    HttpHeader(name.trim(), value.trim())
                }

        /** OkHttp resolves relative paths against the base, which needs a trailing slash. */
        fun normaliseBaseUrl(raw: String): String {
            val trimmed = raw.trim()
            return if (trimmed.isEmpty() || trimmed.endsWith("/")) trimmed else "$trimmed/"
        }
    }
}

/** Persistence handed to the app layer, which backs it with SharedPreferences. */
interface ServerSettingsStorage {
    fun loadRaw(): String?
    fun saveRaw(json: String)
}
