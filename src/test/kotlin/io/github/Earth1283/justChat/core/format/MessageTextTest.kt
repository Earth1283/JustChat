package io.github.Earth1283.justChat.core.format

import io.github.Earth1283.justChat.core.plain
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageTextTest {
    @Test
    fun `without permission the message stays literal`() {
        val c = MessageText.build("&cred <green>green", allowLegacy = false, allowMiniMessage = false, preferred = null)
        assertEquals("&cred <green>green", c.plain())
        assertNull(c.color())
    }

    @Test
    fun `legacy permission converts ampersand codes`() {
        val c = MessageText.build("&chello", allowLegacy = true, allowMiniMessage = false, preferred = null)
        assertEquals("hello", c.plain())
    }

    @Test
    fun `minimessage permission allows colors but strips dangerous tags`() {
        val c = MessageText.build("<red>hi <click:run_command:'/op me'>click</click>", allowLegacy = false, allowMiniMessage = true, preferred = null)
        assertEquals("hi <click:run_command:'/op me'>click</click>", c.plain())
    }

    @Test
    fun `both permissions combine legacy and minimessage`() {
        val c = MessageText.build("&ahello <bold>world", allowLegacy = true, allowMiniMessage = true, preferred = null)
        assertEquals("hello world", c.plain())
    }

    @Test
    fun `hex legacy codes convert`() {
        assertEquals("<#ff8800>x", MessageText.legacyToMiniMessage("&#ff8800x"))
    }

    @Test
    fun `preferred color applies only where the message has none`() {
        val c = MessageText.build("plain", allowLegacy = false, allowMiniMessage = false, preferred = NamedTextColor.AQUA)
        assertEquals(NamedTextColor.AQUA, c.color())
    }

    @Test
    fun `section signs are stripped`() {
        val c = MessageText.build("§chello", allowLegacy = false, allowMiniMessage = false, preferred = null)
        assertEquals("chello", c.plain())
    }
}
