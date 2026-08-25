package dev.dublinbustracker.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerSettingsTest {
    @Test
    fun `header map drops blank rows and trims whitespace`() {
        val settings = ServerSettings(
            baseUrl = "https://example.com/",
            headers = listOf(
                HttpHeader(" X-Api-Key ", " secret "),
                HttpHeader("", "orphan value"),
                HttpHeader("CF-Access-Client-Id", "abc"),
            ),
        )

        assertEquals(
            mapOf("X-Api-Key" to "secret", "CF-Access-Client-Id" to "abc"),
            settings.headerMap(),
        )
    }

    @Test
    fun `settings round trip through json`() {
        val settings = ServerSettings("https://buses.example.com/", listOf(HttpHeader("X-Api-Key", "k")))
        assertEquals(settings, ServerSettings.fromJson(settings.toJson()))
    }

    @Test
    fun `malformed json decodes to null rather than throwing`() {
        assertNull(ServerSettings.fromJson("not json"))
    }

    @Test
    fun `header spec parsing seeds defaults from build config`() {
        val headers = ServerSettings.parseHeaderSpec("X-Api-Key=secret, CF-Access-Client-Id=abc=def")
        assertEquals(
            listOf(HttpHeader("X-Api-Key", "secret"), HttpHeader("CF-Access-Client-Id", "abc=def")),
            headers,
        )
    }

    @Test
    fun `header spec ignores blank and malformed entries`() {
        assertEquals(emptyList(), ServerSettings.parseHeaderSpec(""))
        assertEquals(emptyList(), ServerSettings.parseHeaderSpec("no-equals-sign"))
    }

    @Test
    fun `base url gains a trailing slash so relative paths resolve`() {
        assertEquals("https://x.com/", ServerSettings.normaliseBaseUrl("https://x.com"))
        assertEquals("https://x.com/", ServerSettings.normaliseBaseUrl("  https://x.com/  "))
        assertEquals("", ServerSettings.normaliseBaseUrl("   "))
    }

    @Test
    fun `a complete configuration validates cleanly`() {
        val settings = ServerSettings("https://x.com/", listOf(HttpHeader("X-Api-Key", "k")))
        assertEquals(emptyList(), settings.validate())
        assertTrue(settings.isConfigured)
    }

    @Test
    fun `a missing or non-http address is reported`() {
        assertFalse(ServerSettings.EMPTY.isConfigured)
        assertTrue(ServerSettings.EMPTY.validate().single().contains("required"))
        assertTrue(ServerSettings("ftp://x.com").validate().single().contains("http://"))
    }

    @Test
    fun `bad header rows are reported`() {
        val missingName = ServerSettings("https://x.com/", listOf(HttpHeader("", "value")))
        assertTrue(missingName.validate().single().contains("without a name"))

        val badName = ServerSettings("https://x.com/", listOf(HttpHeader("bad header", "value")))
        assertTrue(badName.validate().single().contains("invalid characters"))
    }

    @Test
    fun `a fully empty header row is ignored rather than flagged`() {
        assertEquals(emptyList(), ServerSettings("https://x.com/", listOf(HttpHeader("", ""))).validate())
    }
}

class ServerSettingsRepositoryTest {
    private class FakeStorage(var raw: String? = null) : ServerSettingsStorage {
        override fun loadRaw(): String? = raw
        override fun saveRaw(json: String) {
            raw = json
        }
    }

    @Test
    fun `defaults are used when nothing has been saved`() {
        val defaults = ServerSettings("https://default.example/", listOf(HttpHeader("X-Api-Key", "d")))
        val repository = ServerSettingsRepository(FakeStorage(), defaults)
        assertEquals(defaults, repository.current())
    }

    @Test
    fun `saved settings win over defaults`() {
        val saved = ServerSettings("https://saved.example/", listOf(HttpHeader("X-Api-Key", "s")))
        val repository = ServerSettingsRepository(FakeStorage(saved.toJson()), ServerSettings("https://default/"))
        assertEquals(saved, repository.current())
    }

    @Test
    fun `updating persists and normalises the base url`() {
        val storage = FakeStorage()
        val repository = ServerSettingsRepository(storage, ServerSettings.EMPTY)

        repository.update(ServerSettings("https://new.example", listOf(HttpHeader("X-Api-Key", "k"))))

        assertEquals("https://new.example/", repository.current().baseUrl)
        assertEquals(repository.current(), ServerSettings.fromJson(storage.raw!!))
    }

    @Test
    fun `corrupt stored settings fall back to defaults`() {
        val defaults = ServerSettings("https://default.example/")
        val repository = ServerSettingsRepository(FakeStorage("{{{"), defaults)
        assertEquals(defaults, repository.current())
    }
}
