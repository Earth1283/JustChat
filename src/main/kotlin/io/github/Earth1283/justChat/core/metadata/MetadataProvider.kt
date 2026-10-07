package io.github.Earth1283.justChat.core.metadata

import io.github.Earth1283.justChat.core.model.ChatMetadata
import java.util.UUID

fun interface MetadataProvider {
    fun get(uuid: UUID): ChatMetadata?
}
