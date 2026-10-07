package io.github.Earth1283.justChat.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConfigParserTest {
    @Test
    fun `an empty file yields the defaults`() {
        val result = ConfigParser.parse(emptyMap())
        assertEquals(JustChatConfig.DEFAULT, result.config)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `values override defaults`() {
        val result = ConfigParser.parse(
            mapOf(
                "chat.format" to "<player>: <message>",
                "placeholders.unresolved" to "empty",
                "continuity.sqlite" to false,
                "metadata.default-group" to "guest",
            ),
        )
        val config = assertNotNull(result.config)
        assertEquals("<player>: <message>", config.chat.format)
        assertEquals(UnresolvedPolicy.EMPTY, config.unresolved)
        assertFalse(config.sqliteEnabled)
        assertEquals("guest", config.metadataDefaults.group)
    }

    @Test
    fun `a format without message is rejected`() {
        val result = ConfigParser.parse(mapOf("chat.format" to "<player>"))
        assertNull(result.config)
        assertTrue(result.errors.single().contains("<message>"))
    }

    @Test
    fun `wrong types reject the whole file`() {
        assertNull(ConfigParser.parse(mapOf("ignore.enabled" to "maybe")).config)
        assertNull(ConfigParser.parse(mapOf("placeholders.unresolved" to "DELETE")).config)
    }

    @Test
    fun `unknown keys warn but do not reject`() {
        val result = ConfigParser.parse(mapOf("chat.formt" to "x"))
        assertNotNull(result.config)
        assertTrue(result.warnings.single().contains("chat.formt"))
    }
}
