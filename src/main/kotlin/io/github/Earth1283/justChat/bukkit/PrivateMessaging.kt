package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.format.ChatPipeline
import io.github.Earth1283.justChat.core.social.IgnoreService
import io.github.Earth1283.justChat.core.social.ReplyTracker
import org.bukkit.entity.Player

class PrivateMessaging(
    private val configManager: ConfigManager,
    private val pipeline: ChatPipeline,
    private val sessions: PlayerSessions,
    private val styles: MessageStyles,
    private val ignores: IgnoreService,
    private val replies: ReplyTracker,
    private val sender: ComponentSender,
    private val messages: MessageService,
) {
    fun send(from: Player, to: Player, text: String) {
        val config = configManager.current.config
        if (!config.privateMessages.enabled) {
            messages.send(from, "pm-disabled")
            return
        }
        if (from.uniqueId == to.uniqueId) {
            messages.send(from, "pm-self")
            return
        }
        if (config.ignoreEnabled && ignores.isIgnoring(from.uniqueId, to.uniqueId)) {
            messages.send(from, "pm-you-ignore-target", "target" to to.name)
            return
        }

        val render = pipeline.renderPrivate(sessions.snapshot(from), sessions.snapshot(to), text, styles.of(from))
        sender.send(from, render.forSender)

        val receiverIgnoresSender = config.ignoreEnabled &&
            ignores.isIgnoring(to.uniqueId, from.uniqueId) &&
            !from.hasPermission(Permissions.IGNORE_BYPASS)
        if (!receiverIgnoresSender) sender.send(to, render.forReceiver)
        replies.recordMessage(from.uniqueId, to.uniqueId, delivered = !receiverIgnoresSender)
    }
}
