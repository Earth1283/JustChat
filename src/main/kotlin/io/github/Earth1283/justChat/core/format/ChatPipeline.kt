package io.github.Earth1283.justChat.core.format

import io.github.Earth1283.justChat.core.config.ConfigManager
import io.github.Earth1283.justChat.core.config.ConfigState
import io.github.Earth1283.justChat.core.metadata.MetadataResolver
import io.github.Earth1283.justChat.core.model.CapabilityHealth
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import java.util.UUID
import java.util.concurrent.atomic.LongAdder

class PlayerSnapshot(
    val uuid: UUID,
    val username: String,
    val displayName: String,
    val world: String?,
    val subject: Any?,
)

class MessageStyle(
    val allowLegacyColors: Boolean,
    val allowMiniMessage: Boolean,
    val preferredColor: TextColor?,
)

class PrivateMessageRender(val forSender: Component, val forReceiver: Component)

class ChatPipeline(
    private val configManager: ConfigManager,
    private val metadataResolver: MetadataResolver,
) {
    @Volatile
    var externalPlaceholders: ExternalPlaceholders? = null

    @Volatile
    private var lastRenderUsedEmergency = false

    private val emergencyRenders = LongAdder()

    val emergencyRenderCount: Long get() = emergencyRenders.sum()

    fun renderPublic(sender: PlayerSnapshot, rawMessage: String, style: MessageStyle): Component {
        val runtime = configManager.current
        val context = FormatContext(
            sender = party(sender),
            receiver = null,
            message = messageComponent(rawMessage, style),
            external = externalPlaceholders,
            unresolved = runtime.config.unresolved,
        )
        return renderOrFallBack { runtime.chatTemplate.render(context) } ?: emergency(Emergency.render(context))
    }

    fun renderPrivate(
        sender: PlayerSnapshot,
        receiver: PlayerSnapshot,
        rawMessage: String,
        style: MessageStyle,
    ): PrivateMessageRender {
        val runtime = configManager.current
        val context = FormatContext(
            sender = party(sender),
            receiver = party(receiver),
            message = messageComponent(rawMessage, style),
            external = externalPlaceholders,
            unresolved = runtime.config.unresolved,
        )
        val forSender = renderOrFallBack { runtime.privateSenderTemplate.render(context) }
            ?: emergency(Emergency.renderPrivate(context, outgoing = true))
        val forReceiver = renderOrFallBack { runtime.privateReceiverTemplate.render(context) }
            ?: emergency(Emergency.renderPrivate(context, outgoing = false))
        return PrivateMessageRender(forSender, forReceiver)
    }

    fun health(): CapabilityHealth {
        val configIsClean = configManager.status.state == ConfigState.LOADED
        return if (configIsClean && !lastRenderUsedEmergency) CapabilityHealth.HEALTHY else CapabilityHealth.DEGRADED
    }

    private fun party(player: PlayerSnapshot) = Party(
        uuid = player.uuid,
        username = player.username,
        displayName = player.displayName,
        world = player.world,
        metadata = metadataResolver.resolve(player.uuid, player.username),
        subject = player.subject,
    )

    private fun messageComponent(raw: String, style: MessageStyle): Component =
        MessageText.build(raw, style.allowLegacyColors, style.allowMiniMessage, style.preferredColor)

    private inline fun renderOrFallBack(render: () -> Component): Component? =
        try {
            render().also { lastRenderUsedEmergency = false }
        } catch (_: Throwable) {
            null
        }

    private fun emergency(component: Component): Component {
        emergencyRenders.increment()
        lastRenderUsedEmergency = true
        return component
    }
}
