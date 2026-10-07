package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.metadata.MemoryMetadataCache
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.persistence.PersistenceManager
import io.github.Earth1283.justChat.core.social.ChatColorPreferences
import io.github.Earth1283.justChat.core.social.IgnoreService
import io.github.Earth1283.justChat.core.social.ReplyTracker
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

class PlayerStateListener(
    private val persistence: PersistenceManager,
    private val memoryCache: MemoryMetadataCache,
    private val resolver: MetadataResolver,
    private val ignores: IgnoreService,
    private val preferences: ChatColorPreferences,
    private val replies: ReplyTracker,
    private val sessions: PlayerSessions,
) : Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
        val stored = persistence.loadPlayer(event.uniqueId, PRELOGIN_LOAD_TIMEOUT_MILLIS) ?: return
        stored.metadata?.let { memoryCache.putStored(event.uniqueId, it.metadata, it.username) }
        ignores.load(event.uniqueId, stored.ignoredNamesByUuid)
        preferences.load(event.uniqueId, stored.chatColor)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        memoryCache.pin(player.uniqueId)
        sessions.enter(player)
        resolver.resolve(player.uniqueId, player.name)
    }

    @EventHandler
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        sessions.changeWorld(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        val id = event.player.uniqueId
        memoryCache.unpin(id)
        sessions.leave(event.player)
        ignores.unload(id)
        preferences.unload(id)
        replies.forget(id)
    }

    private companion object {
        const val PRELOGIN_LOAD_TIMEOUT_MILLIS = 1_500L
    }
}
