package io.github.Earth1283.justChat.bukkit.integration

import io.github.Earth1283.justChat.core.metadata.MetadataProvider
import io.github.Earth1283.justChat.core.model.ChatMetadata
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import java.util.UUID

internal object LuckPermsAdapter {
    fun createProvider(): MetadataProvider? {
        val luckPerms = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)?.provider ?: return null
        return LuckPermsMetadataProvider(luckPerms)
    }
}

private class LuckPermsMetadataProvider(private val luckPerms: LuckPerms) : MetadataProvider {
    override fun get(uuid: UUID): ChatMetadata? {
        val user = luckPerms.userManager.getUser(uuid) ?: return null
        val metaData = user.cachedData.metaData
        return ChatMetadata(metaData.prefix, metaData.suffix, metaData.primaryGroup)
    }
}
