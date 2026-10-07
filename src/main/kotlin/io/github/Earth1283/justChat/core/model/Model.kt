package io.github.Earth1283.justChat.core.model

data class ChatMetadata(
    val prefix: String?,
    val suffix: String?,
    val primaryGroup: String?,
)

enum class MetadataSource(val label: String) {
    LUCKPERMS_API("LuckPerms API"),
    VAULT_API("Vault API"),
    MEMORY_CACHE("Memory cache"),
    SQLITE_CACHE("SQLite cache"),
    DEFAULT("Configured defaults"),
    ;

    val isLive: Boolean get() = this == LUCKPERMS_API || this == VAULT_API
}

data class ResolvedMetadata(
    val prefix: String,
    val suffix: String,
    val group: String,
    val source: MetadataSource,
)

enum class IntegrationHealth { HEALTHY, UNAVAILABLE, FAILED }

enum class CapabilityHealth { HEALTHY, DEGRADED, FAILED }
