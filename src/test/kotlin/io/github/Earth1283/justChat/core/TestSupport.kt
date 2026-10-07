package io.github.Earth1283.justChat.core

import io.github.Earth1283.justChat.core.config.UnresolvedPolicy
import io.github.Earth1283.justChat.core.format.ExternalPlaceholders
import io.github.Earth1283.justChat.core.format.FormatContext
import io.github.Earth1283.justChat.core.format.Party
import io.github.Earth1283.justChat.core.model.MetadataSource
import io.github.Earth1283.justChat.core.model.ResolvedMetadata
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.UUID

fun Component.plain(): String = PlainTextComponentSerializer.plainText().serialize(this)

fun party(
    name: String = "Steve",
    displayName: String = name,
    world: String? = "world",
    prefix: String = "",
    suffix: String = "",
    group: String = "default",
    uuid: UUID = UUID.nameUUIDFromBytes(name.toByteArray()),
) = Party(uuid, name, displayName, world, ResolvedMetadata(prefix, suffix, group, MetadataSource.DEFAULT), null)

fun context(
    sender: Party = party(),
    receiver: Party? = null,
    message: String = "hello",
    external: ExternalPlaceholders? = null,
    unresolved: UnresolvedPolicy = UnresolvedPolicy.KEEP,
) = FormatContext(sender, receiver, Component.text(message), external, unresolved)
