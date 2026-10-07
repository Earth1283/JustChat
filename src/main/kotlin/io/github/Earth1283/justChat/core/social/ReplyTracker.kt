package io.github.Earth1283.justChat.core.social

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ReplyTracker {
    private val lastPartnerByPlayer = ConcurrentHashMap<UUID, UUID>()

    fun recordMessage(sender: UUID, receiver: UUID, delivered: Boolean) {
        lastPartnerByPlayer[sender] = receiver
        if (delivered) lastPartnerByPlayer[receiver] = sender
    }

    fun lastPartnerOf(player: UUID): UUID? = lastPartnerByPlayer[player]

    fun forget(player: UUID) {
        lastPartnerByPlayer.remove(player)
    }
}
