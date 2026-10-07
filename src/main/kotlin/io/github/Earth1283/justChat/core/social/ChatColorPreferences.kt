package io.github.Earth1283.justChat.core.social

import io.github.Earth1283.justChat.core.format.ChatColors
import io.github.Earth1283.justChat.core.persistence.PersistenceManager
import net.kyori.adventure.text.format.TextColor
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ChatColorPreferences(private val persistence: PersistenceManager) {
    private val colorByPlayer = ConcurrentHashMap<UUID, TextColor>()

    fun load(player: UUID, storedColor: String?) {
        val color = storedColor?.let(ChatColors::parse) ?: return
        colorByPlayer[player] = color
    }

    fun unload(player: UUID) {
        colorByPlayer.remove(player)
    }

    fun colorOf(player: UUID): TextColor? = colorByPlayer[player]

    fun set(player: UUID, color: TextColor) {
        colorByPlayer[player] = color
        persistence.queueColor(player, ChatColors.serialize(color))
    }

    fun reset(player: UUID) {
        colorByPlayer.remove(player)
        persistence.queueColor(player, null)
    }
}
