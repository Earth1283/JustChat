package io.github.Earth1283.justChat.core.social

import io.github.Earth1283.justChat.core.persistence.IgnoreOp
import io.github.Earth1283.justChat.core.persistence.PersistenceManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class IgnoredPlayer(val uuid: UUID, val name: String)

class IgnoreService(private val persistence: PersistenceManager) {
    private val ignoredByOwner = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, String>>()

    fun load(owner: UUID, ignoredNamesByUuid: Map<UUID, String>) {
        if (ignoredNamesByUuid.isEmpty()) return
        ignoredByOwner.computeIfAbsent(owner) { ConcurrentHashMap() }.putAll(ignoredNamesByUuid)
    }

    fun unload(owner: UUID) {
        ignoredByOwner.remove(owner)
    }

    fun isIgnoring(owner: UUID, target: UUID): Boolean = ignoredByOwner[owner]?.containsKey(target) == true

    fun ignore(owner: UUID, target: UUID, targetName: String): Boolean {
        val ignored = ignoredByOwner.computeIfAbsent(owner) { ConcurrentHashMap() }
        val wasAlreadyIgnored = ignored.put(target, targetName) != null
        if (wasAlreadyIgnored) return false
        persistence.queueIgnore(IgnoreOp.Add(owner, target, targetName))
        return true
    }

    fun unignoreByName(owner: UUID, name: String): IgnoredPlayer? {
        val ignored = ignoredByOwner[owner] ?: return null
        val match = ignored.entries.firstOrNull { it.value.equals(name, ignoreCase = true) } ?: return null
        ignored.remove(match.key)
        persistence.queueIgnore(IgnoreOp.Remove(owner, match.key))
        return IgnoredPlayer(match.key, match.value)
    }

    fun ignoredNames(owner: UUID): List<String> =
        ignoredByOwner[owner]?.values?.sortedBy { it.lowercase() } ?: emptyList()
}
