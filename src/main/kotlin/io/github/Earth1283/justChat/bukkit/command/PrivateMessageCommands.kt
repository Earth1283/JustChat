package io.github.Earth1283.justChat.bukkit.command

import io.github.Earth1283.justChat.bukkit.MessageService
import io.github.Earth1283.justChat.bukkit.Permissions
import io.github.Earth1283.justChat.bukkit.PrivateMessaging
import io.github.Earth1283.justChat.core.social.ReplyTracker
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

internal fun visibleOnlinePlayerNames(viewer: CommandSender, prefix: String): List<String> =
    Bukkit.getOnlinePlayers()
        .filter { viewer !is Player || viewer.canSee(it) }
        .map { it.name }
        .filter { it.startsWith(prefix, ignoreCase = true) }
        .sorted()

internal fun findVisiblePlayer(viewer: Player, name: String): Player? =
    Bukkit.getPlayerExact(name)?.takeIf { viewer.canSee(it) }

class MessageCommand(
    private val privateMessaging: PrivateMessaging,
    private val messages: MessageService,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) return messages.tell(sender, "players-only")
        if (!sender.hasPermission(Permissions.MESSAGE)) return messages.tell(sender, "no-permission")
        if (args.size < 2) return messages.tell(sender, "usage-msg")

        val target = findVisiblePlayer(sender, args[0])
            ?: return messages.tell(sender, "player-not-found", "target" to args[0])
        privateMessaging.send(sender, target, args.drop(1).joinToString(" "))
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> =
        if (args.size == 1) visibleOnlinePlayerNames(sender, args[0]) else emptyList()
}

class ReplyCommand(
    private val privateMessaging: PrivateMessaging,
    private val replies: ReplyTracker,
    private val messages: MessageService,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) return messages.tell(sender, "players-only")
        if (!sender.hasPermission(Permissions.MESSAGE)) return messages.tell(sender, "no-permission")
        if (args.isEmpty()) return messages.tell(sender, "usage-reply")

        val partner = replies.lastPartnerOf(sender.uniqueId)
            ?.let(Bukkit::getPlayer)
            ?.takeIf { sender.canSee(it) }
            ?: return messages.tell(sender, "pm-no-reply-target")
        privateMessaging.send(sender, partner, args.joinToString(" "))
        return true
    }
}
