package io.github.Earth1283.justChat.bukkit.command

import io.github.Earth1283.justChat.bukkit.ComponentSender
import io.github.Earth1283.justChat.bukkit.MessageService
import io.github.Earth1283.justChat.bukkit.Permissions
import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.ReloadOutcome
import io.github.Earth1283.justChat.core.diagnostics.Diagnostics
import io.github.Earth1283.justChat.core.diagnostics.DiagnosticsRenderer
import io.github.Earth1283.justChat.core.format.ChatColors
import io.github.Earth1283.justChat.core.social.ChatColorPreferences
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class JustChatCommand(
    private val version: String,
    private val configManager: ConfigManager,
    private val diagnostics: Diagnostics,
    private val preferences: ChatColorPreferences,
    private val messages: MessageService,
    private val sender: ComponentSender,
    private val reload: () -> ReloadOutcome,
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        when (args.firstOrNull()?.lowercase()) {
            "status" -> admin(sender) { this.sender.send(sender, DiagnosticsRenderer.status(diagnostics.statusRows())) }
            "doctor" -> admin(sender) { this.sender.send(sender, DiagnosticsRenderer.doctor(diagnostics.findings())) }
            "reload" -> admin(sender) { reloadAsync(sender) }
            "version" -> admin(sender) { this.sender.send(sender, Component.text("JustChat $version", NamedTextColor.GOLD)) }
            "color", "colour" -> color(sender, args.drop(1))
            else -> usage(sender)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<String>): List<String> {
        val options = when (args.size) {
            1 -> buildList {
                if (sender.hasPermission(Permissions.ADMIN)) addAll(listOf("status", "doctor", "reload", "version"))
                if (sender.hasPermission(Permissions.COLOR_PREFERENCE)) add("color")
            }
            2 -> if (args[0].equals("color", ignoreCase = true) && sender.hasPermission(Permissions.COLOR_PREFERENCE)) {
                ChatColors.namedColorNames + "reset"
            } else {
                emptyList()
            }
            else -> emptyList()
        }
        return options.filter { it.startsWith(args.last(), ignoreCase = true) }
    }

    private inline fun admin(target: CommandSender, action: () -> Unit) {
        if (target.hasPermission(Permissions.ADMIN)) action() else messages.send(target, "no-permission")
    }

    private fun usage(target: CommandSender) {
        val subcommands = buildList {
            if (target.hasPermission(Permissions.ADMIN)) addAll(listOf("status", "doctor", "reload", "version"))
            if (target.hasPermission(Permissions.COLOR_PREFERENCE)) add("color")
        }
        sender.send(target, Component.text("Usage: /justchat <${subcommands.joinToString("|")}>", NamedTextColor.GRAY))
    }

    private fun reloadAsync(target: CommandSender) {
        CompletableFuture.runAsync {
            val applied = runCatching { reload().applied }.getOrDefault(false)
            messages.send(target, if (applied) "reload-ok" else "reload-failed")
        }
    }

    private fun color(target: CommandSender, args: List<String>) {
        val player = target as? Player ?: return messages.send(target, "players-only")
        if (!player.hasPermission(Permissions.COLOR_PREFERENCE)) return messages.send(player, "no-permission")
        if (!configManager.current.config.colorsEnabled) return messages.send(player, "color-disabled")
        val choice = args.firstOrNull() ?: return messages.send(player, "color-usage")

        if (choice.equals("reset", ignoreCase = true)) {
            preferences.reset(player.uniqueId)
            return messages.send(player, "color-reset")
        }
        val parsed = ChatColors.parse(choice) ?: return messages.send(player, "color-invalid", "value" to choice)
        preferences.set(player.uniqueId, parsed)
        sender.send(player, messages.renderColored("color-set", parsed, ChatColors.serialize(parsed)))
    }
}
