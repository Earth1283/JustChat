package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.format.MessageStyle
import io.github.Earth1283.justChat.core.social.ChatColorPreferences
import org.bukkit.entity.Player

class MessageStyles(private val configManager: ConfigManager, private val preferences: ChatColorPreferences) {
    fun of(player: Player): MessageStyle {
        if (!configManager.current.config.colorsEnabled) return PLAIN
        val preferred = preferences.colorOf(player.uniqueId)?.takeIf { player.hasPermission(Permissions.COLOR_PREFERENCE) }
        return MessageStyle(
            allowLegacyColors = player.hasPermission(Permissions.CHAT_LEGACY_COLORS),
            allowMiniMessage = player.hasPermission(Permissions.CHAT_MINIMESSAGE),
            preferredColor = preferred,
        )
    }

    private companion object {
        val PLAIN = MessageStyle(allowLegacyColors = false, allowMiniMessage = false, preferredColor = null)
    }
}
