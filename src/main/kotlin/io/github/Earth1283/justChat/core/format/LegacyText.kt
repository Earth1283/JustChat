package io.github.Earth1283.justChat.core.format

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import java.util.concurrent.ConcurrentHashMap

object LegacyText {
    private const val MAX_CACHE = 1024

    private val serializer = LegacyComponentSerializer.builder()
        .character('&')
        .hexColors()
        .useUnusualXRepeatedCharacterHexFormat()
        .build()

    private val cache = ConcurrentHashMap<String, Component>()

    fun toComponent(text: String?): Component {
        if (text.isNullOrEmpty()) return Component.empty()
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0) return Component.text(text)
        cache[text]?.let { return it }
        val parsed = serializer.deserialize(text.replace('§', '&'))
        if (cache.size >= MAX_CACHE) cache.clear()
        cache[text] = parsed
        return parsed
    }
}
