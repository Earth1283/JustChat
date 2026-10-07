package io.github.Earth1283.justChat.bukkit.command

import io.github.Earth1283.justChat.bukkit.MessageService
import org.bukkit.command.CommandSender

internal fun MessageService.tell(target: CommandSender, key: String, vararg values: Pair<String, String>): Boolean {
    send(target, key, *values)
    return true
}
