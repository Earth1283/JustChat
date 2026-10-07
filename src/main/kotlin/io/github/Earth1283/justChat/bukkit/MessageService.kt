package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.Messages
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import org.bukkit.command.CommandSender

class MessageService(private val configManager: ConfigManager, private val sender: ComponentSender) {
    private val miniMessage = MiniMessage.miniMessage()

    fun render(key: String, vararg values: Pair<String, String>): Component {
        val template = configManager.current.config.messages[key] ?: Messages.DEFAULTS.getValue(key)
        val resolvers = values.map { (name, value) -> Placeholder.unparsed(name, value) }
        return miniMessage.deserialize(template, TagResolver.resolver(resolvers))
    }

    fun renderColored(key: String, color: TextColor, colorName: String): Component {
        val template = configManager.current.config.messages[key] ?: Messages.DEFAULTS.getValue(key)
        return miniMessage.deserialize(
            template,
            Placeholder.unparsed("chosen_name", colorName),
            TagResolver.resolver("chosen", Tag.styling(color)),
        )
    }

    fun send(target: CommandSender, key: String, vararg values: Pair<String, String>) {
        sender.send(target, render(key, *values))
    }
}
