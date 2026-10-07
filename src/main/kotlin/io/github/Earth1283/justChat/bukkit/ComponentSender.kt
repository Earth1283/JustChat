package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.format.LegacyText
import io.github.Earth1283.justChat.core.format.MessageText
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.json.JSONOptions
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.chat.ComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

class PreparedMessage(val components: Array<BaseComponent>, val legacyText: String)

class ComponentSender(private val serverDataVersion: Int) {
    private val json by lazy {
        GsonComponentSerializer.builder()
            .options(JSONOptions.byDataVersion().at(serverDataVersion))
            .build()
    }

    private val legacy = LegacyComponentSerializer.legacySection()

    fun prepare(component: Component): PreparedMessage =
        PreparedMessage(ComponentSerializer.parse(json.serialize(component)), legacy.serialize(component))

    fun deliver(target: CommandSender, message: PreparedMessage) {
        if (target is Player) target.spigot().sendMessage(*message.components) else target.sendMessage(message.legacyText)
    }

    fun send(target: CommandSender, component: Component) = deliver(target, prepare(component))

    fun warmUpAsync() {
        CompletableFuture.runAsync {
            runCatching {
                prepare(Component.text("warm-up", NamedTextColor.GRAY).hoverEvent(HoverEvent.showText(Component.text("up"))))
                MessageText.build("&awarm <red>up", allowLegacy = true, allowMiniMessage = true, preferred = null)
                LegacyText.toComponent("&7warm")
            }
        }
    }
}
