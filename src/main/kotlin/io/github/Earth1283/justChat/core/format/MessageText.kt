package io.github.Earth1283.justChat.core.format

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

object MessageText {
    private val restricted: MiniMessage = MiniMessage.builder()
        .tags(
            TagResolver.resolver(
                StandardTags.color(),
                StandardTags.decorations(),
                StandardTags.gradient(),
                StandardTags.rainbow(),
                StandardTags.reset(),
            ),
        )
        .build()

    private val legacy = LegacyComponentSerializer.builder().character('&').hexColors().build()

    private val LEGACY_TAGS = mapOf(
        '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua",
        '4' to "dark_red", '5' to "dark_purple", '6' to "gold", '7' to "gray",
        '8' to "dark_gray", '9' to "blue", 'a' to "green", 'b' to "aqua",
        'c' to "red", 'd' to "light_purple", 'e' to "yellow", 'f' to "white",
        'k' to "obfuscated", 'l' to "bold", 'm' to "strikethrough", 'n' to "underlined",
        'o' to "italic", 'r' to "reset",
    )

    fun build(raw: String, allowLegacy: Boolean, allowMiniMessage: Boolean, preferred: TextColor?): Component {
        val text = if (raw.indexOf('§') >= 0) raw.replace("§", "") else raw
        val component = when {
            allowMiniMessage -> restricted.deserialize(if (allowLegacy) legacyToMiniMessage(text) else text)
            allowLegacy -> legacy.deserialize(text)
            else -> Component.text(text)
        }
        return if (preferred != null) component.colorIfAbsent(preferred) else component
    }

    internal fun legacyToMiniMessage(s: String): String {
        if (s.indexOf('&') < 0) return s
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '&' && i + 1 < s.length) {
                val n = s[i + 1]
                if (n == '#' && isHex(s, i + 2, 6)) {
                    sb.append("<#").append(s, i + 2, i + 8).append('>')
                    i += 8
                    continue
                }
                val tag = LEGACY_TAGS[n.lowercaseChar()]
                if (tag != null) {
                    sb.append('<').append(tag).append('>')
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        return sb.toString()
    }

    private fun isHex(s: String, from: Int, len: Int): Boolean {
        if (from + len > s.length) return false
        for (k in from until from + len) if (Character.digit(s[k], 16) < 0) return false
        return true
    }
}
