package io.github.Earth1283.justChat.bukkit.integration

import io.github.Earth1283.justChat.bukkit.PlayerSessions
import io.github.Earth1283.justChat.core.metadata.MetadataProvider
import io.github.Earth1283.justChat.core.model.ChatMetadata
import net.milkbowl.vault.chat.Chat
import org.bukkit.Bukkit
import java.util.UUID

internal object VaultAdapter {
    fun createProvider(sessions: PlayerSessions): MetadataProvider? {
        val chat = Bukkit.getServicesManager().getRegistration(Chat::class.java)?.provider ?: return null
        return VaultMetadataProvider(chat, sessions)
    }
}

private class VaultMetadataProvider(private val chat: Chat, private val sessions: PlayerSessions) : MetadataProvider {
    override fun get(uuid: UUID): ChatMetadata? {
        val player = Bukkit.getPlayer(uuid) ?: return null
        val world = sessions.worldOf(uuid)
        return ChatMetadata(
            prefix = chat.getPlayerPrefix(world, player),
            suffix = chat.getPlayerSuffix(world, player),
            primaryGroup = primaryGroupOrNull(world, player),
        )
    }

    private fun primaryGroupOrNull(world: String?, player: org.bukkit.OfflinePlayer): String? = try {
        chat.getPrimaryGroup(world, player)
    } catch (_: UnsupportedOperationException) {
        null
    }
}
