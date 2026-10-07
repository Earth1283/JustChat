package io.github.Earth1283.justChat.core.persistence

import io.github.Earth1283.justChat.core.model.ChatMetadata
import java.util.UUID

class MetadataRow(
    val uuid: UUID,
    val username: String?,
    val metadata: ChatMetadata,
    val sourceName: String,
    val updatedAt: Long,
)

class ColorRow(val uuid: UUID, val color: String?) {
    val isReset: Boolean get() = color == null
}

sealed interface IgnoreOp {
    val owner: UUID
    val target: UUID

    class Add(override val owner: UUID, override val target: UUID, val targetName: String) : IgnoreOp
    class Remove(override val owner: UUID, override val target: UUID) : IgnoreOp
}

class WriteBatch(
    val metadata: List<MetadataRow>,
    val colors: List<ColorRow>,
    val ignoreOpsInOrder: List<IgnoreOp>,
) {
    val isEmpty: Boolean get() = metadata.isEmpty() && colors.isEmpty() && ignoreOpsInOrder.isEmpty()
}

class StoredPlayer(
    val metadata: MetadataRow?,
    val chatColor: String?,
    val ignoredNamesByUuid: Map<UUID, String>,
)

interface ContinuityStore : AutoCloseable {
    fun loadPlayer(uuid: UUID): StoredPlayer
    fun write(batch: WriteBatch)

    fun hasLiveHistory(): Boolean
}
