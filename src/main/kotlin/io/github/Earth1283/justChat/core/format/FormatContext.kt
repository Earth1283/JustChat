package io.github.Earth1283.justChat.core.format

import io.github.Earth1283.justChat.core.config.UnresolvedPolicy
import io.github.Earth1283.justChat.core.model.ResolvedMetadata
import net.kyori.adventure.text.Component
import java.util.UUID

fun interface ExternalPlaceholders {
    fun expand(subject: Any?, placeholder: String): String?
}

class Party(
    val uuid: UUID,
    val username: String,
    val displayName: String,
    val world: String?,
    val metadata: ResolvedMetadata,
    val subject: Any?,
)

class FormatContext(
    val sender: Party,
    val receiver: Party?,
    val message: Component,
    val external: ExternalPlaceholders?,
    val unresolved: UnresolvedPolicy,
)

enum class Placeholder(val tag: String) {
    PLAYER("player"),
    USERNAME("username"),
    DISPLAY_NAME("display_name"),
    MESSAGE("message"),
    WORLD("world"),
    PREFIX("prefix"),
    SUFFIX("suffix"),
    GROUP("group"),
    UUID("uuid"),

    SENDER("sender"),
    RECEIVER("receiver"),
    RECEIVER_DISPLAY_NAME("receiver_display_name"),
    RECEIVER_PREFIX("receiver_prefix"),
    RECEIVER_SUFFIX("receiver_suffix"),
    RECEIVER_GROUP("receiver_group"),
    ;

    companion object {
        val CHAT: Set<Placeholder> = setOf(PLAYER, USERNAME, DISPLAY_NAME, MESSAGE, WORLD, PREFIX, SUFFIX, GROUP, UUID)
        val PRIVATE_MESSAGE: Set<Placeholder> = entries.toSet()
    }
}
