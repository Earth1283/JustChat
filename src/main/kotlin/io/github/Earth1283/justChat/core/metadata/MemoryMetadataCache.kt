package io.github.Earth1283.justChat.core.metadata

import io.github.Earth1283.justChat.core.model.ChatMetadata
import io.github.Earth1283.justChat.core.model.MetadataSource
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MemoryMetadataCache(private val maxEntries: Int = 4096) {
    class Entry(
        val metadata: ChatMetadata,
        val username: String?,
        val liveSource: MetadataSource?,
    ) {
        val fromStore: Boolean get() = liveSource == null
    }

    private val entries = ConcurrentHashMap<UUID, Entry>()
    private val pinned: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    val size: Int get() = entries.size
    val pinnedCount: Int get() = pinned.size

    fun get(uuid: UUID): Entry? = entries[uuid]

    fun putLive(uuid: UUID, metadata: ChatMetadata, username: String?, source: MetadataSource): Boolean {
        val old = entries[uuid]
        if (old != null && old.metadata == metadata && old.username == username && old.liveSource == source) return false
        entries[uuid] = Entry(metadata, username, source)
        evictIfNeeded()
        return true
    }

    fun putStored(uuid: UUID, metadata: ChatMetadata, username: String?) {
        entries.putIfAbsent(uuid, Entry(metadata, username, null))
        evictIfNeeded()
    }

    fun pin(uuid: UUID) {
        pinned += uuid
    }

    fun unpin(uuid: UUID) {
        pinned -= uuid
        evictIfNeeded()
    }

    private fun evictIfNeeded() {
        if (entries.size <= maxEntries) return
        val target = maxEntries - maxEntries / 10
        val it = entries.keys.iterator()
        while (entries.size > target && it.hasNext()) {
            val k = it.next()
            if (k !in pinned) entries.remove(k)
        }
    }
}
