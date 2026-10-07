package io.github.Earth1283.justChat.bukkit.command

import io.github.Earth1283.justChat.bukkit.MessageService
import io.github.Earth1283.justChat.bukkit.Permissions
import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.social.IgnoreService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

private fun requireIgnoreAccess(sender: CommandSender, configManager: ConfigManager, messages: MessageService): Player? {
    if (sender !is Player) {
        messages.send(sender, "players-only")
        return null
    }
    if (!sender.hasPermission(Permissions.IGNORE)) {
        messages.send(sender, "no-permission")
        return null
    }
    if (!configManager.current.config.ignoreEnabled) {
        messages.send(sender, "ignore-disabled")
        return null
    }
    return sender
}

class IgnoreCommand(
    private val configManager: ConfigManager,
    private val ignores: IgnoreService,
    private val messages: MessageService,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = requireIgnoreAccess(sender, configManager, messages) ?: return true
        if (args.size != 1) return messages.tell(player, "usage-ignore")

        val target = findVisiblePlayer(player, args[0])
            ?: return messages.tell(player, "player-not-found", "target" to args[0])
        when {
            target.uniqueId == player.uniqueId -> messages.send(player, "ignore-self")
            target.hasPermission(Permissions.IGNORE_BYPASS) -> messages.send(player, "ignore-exempt", "target" to target.name)
            ignores.ignore(player.uniqueId, target.uniqueId, target.name) -> messages.send(player, "ignore-added", "target" to target.name)
            else -> messages.send(player, "ignore-already", "target" to target.name)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> =
        if (args.size == 1) visibleOnlinePlayerNames(sender, args[0]) else emptyList()
}

class UnignoreCommand(
    private val configManager: ConfigManager,
    private val ignores: IgnoreService,
    private val messages: MessageService,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = requireIgnoreAccess(sender, configManager, messages) ?: return true
        if (args.size != 1) return messages.tell(player, "usage-unignore")

        val removed = ignores.unignoreByName(player.uniqueId, args[0])
        if (removed != null) messages.send(player, "ignore-removed", "target" to removed.name)
        else messages.send(player, "ignore-not-ignoring", "target" to args[0])
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        if (sender !is Player || args.size != 1) return emptyList()
        return ignores.ignoredNames(sender.uniqueId).filter { it.startsWith(args[0], ignoreCase = true) }
    }
}

class IgnoreListCommand(
    private val configManager: ConfigManager,
    private val ignores: IgnoreService,
    private val messages: MessageService,
) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = requireIgnoreAccess(sender, configManager, messages) ?: return true
        val names = ignores.ignoredNames(player.uniqueId)
        if (names.isEmpty()) messages.send(player, "ignore-list-empty")
        else messages.send(player, "ignore-list", "list" to names.joinToString(", "))
        return true
    }
}
