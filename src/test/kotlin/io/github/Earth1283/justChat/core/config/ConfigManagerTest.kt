package io.github.Earth1283.justChat.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigManagerTest {
    @Test
    fun `built-in defaults compile`() {
        val manager = ConfigManager()
        assertEquals(JustChatConfig.DEFAULT, manager.current.config)
    }

    @Test
    fun `a valid reload is applied`() {
        val manager = ConfigManager()
        val outcome = manager.reload { mapOf("chat.format" to "<player>: <message>") }
        assertTrue(outcome.applied)
        assertEquals("<player>: <message>", manager.current.config.chat.format)
        assertEquals(ConfigState.LOADED, manager.status.state)
    }

    @Test
    fun `an invalid reload retains the last known good config`() {
        val manager = ConfigManager()
        manager.reload { mapOf("chat.format" to "<player>: <message>") }
        val outcome = manager.reload { mapOf("chat.format" to "no message here") }
        assertFalse(outcome.applied)
        assertEquals("<player>: <message>", manager.current.config.chat.format)
        assertEquals(ConfigState.RETAINED_PREVIOUS, manager.status.state)
    }

    @Test
    fun `an unreadable file falls back to defaults on first load`() {
        val manager = ConfigManager()
        val outcome = manager.reload { error("while scanning a simple key") }
        assertFalse(outcome.applied)
        assertEquals(ConfigState.DEFAULTS_ONLY, manager.status.state)
        assertEquals(JustChatConfig.DEFAULT, manager.current.config)
    }

    @Test
    fun `a format that cannot be compiled is rejected`() {
        val manager = ConfigManager()
        val outcome = manager.reload { mapOf("chat.format" to "<gradient:red:blue><player></gradient> <message>") }
        assertFalse(outcome.applied)
        assertTrue(manager.status.errors.single().contains("gradient"))
    }
}
