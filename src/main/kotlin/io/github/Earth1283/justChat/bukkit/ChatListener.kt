package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.social.IgnoreService
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import java.util.logging.Logger

class ChatListener(
    private val configManager: ConfigManager,
    private val pipeline: ChatPipeline,
    private val sessions: PlayerSessions,
    private val styles: MessageStyles,
    private val ignores: IgnoreService,
    private val sender: ComponentSender,
    private val logger: Logger,
) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncPlayerChatEvent) {
        val config = configManager.current.config
        if (!config.chat.enabled) return

        val player = event.player
        val prepared = try {
            sender.prepare(pipeline.renderPublic(sessions.snapshot(player), event.message, styles.of(player)))
        } catch (t: Throwable) {
            logger.warning("Chat formatting failed; leaving this message to the server's default format: $t")
            return
        }

        event.isCancelled = true
        val respectIgnores = config.ignoreEnabled && !player.hasPermission(Permissions.IGNORE_BYPASS)
        for (recipient in event.recipients) {
            if (respectIgnores && recipient != player && ignores.isIgnoring(recipient.uniqueId, player.uniqueId)) continue
            deliverSafely(recipient, prepared)
        }
        Bukkit.getConsoleSender().sendMessage(prepared.legacyText)
    }

    private fun deliverSafely(recipient: Player, message: PreparedMessage) {
        try {
            sender.deliver(recipient, message)
        } catch (t: Throwable) {
            logger.fine("Could not deliver chat to ${recipient.name}: $t")
        }
    }
}
