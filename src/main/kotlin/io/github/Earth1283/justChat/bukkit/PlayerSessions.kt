package io.github.Earth1283.justChat.bukkit

import io.github.Earth1283.justChat.core.format.PlayerSnapshot
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerSessions {
    private val worldByPlayer = ConcurrentHashMap<UUID, String>()

    fun enter(player: Player) {
        worldByPlayer[player.uniqueId] = player.world.name
    }

    fun changeWorld(player: Player) = enter(player)

    fun leave(player: Player) {
        worldByPlayer.remove(player.uniqueId)
    }

    fun worldOf(uuid: UUID): String? = worldByPlayer[uuid]

    fun snapshot(player: Player) = PlayerSnapshot(
        uuid = player.uniqueId,
        username = player.name,
        displayName = player.displayName,
        world = worldByPlayer[player.uniqueId],
        subject = player,
    )
}
