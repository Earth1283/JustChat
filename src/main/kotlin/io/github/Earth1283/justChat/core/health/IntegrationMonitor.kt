package io.github.Earth1283.justChat.core.health

import io.github.Earth1283.justChat.core.model.IntegrationHealth
import java.util.concurrent.ConcurrentHashMap

enum class Integration(val label: String) {
    LUCKPERMS("LuckPerms API"),
    VAULT("Vault API"),
    PLACEHOLDERAPI("PlaceholderAPI"),
    SQLITE("SQLite cache"),
}

object IntegrationDetail {
    const val NOT_DETECTED = "not detected"
    const val DISABLED_IN_CONFIG = "disabled in config"
    const val VAULT_WITHOUT_CHAT_PROVIDER = "installed, but no chat provider is registered"
}

class IntegrationState(val health: IntegrationHealth, val detail: String?)

class IntegrationMonitor(private val log: (String) -> Unit = {}) {
    private val states = ConcurrentHashMap<Integration, IntegrationState>().also { map ->
        Integration.entries.forEach { map[it] = IntegrationState(IntegrationHealth.UNAVAILABLE, IntegrationDetail.NOT_DETECTED) }
    }
    private val lastLogged = ConcurrentHashMap<Integration, Long>()

    fun state(i: Integration): IntegrationState = states.getValue(i)

    fun health(i: Integration): IntegrationHealth = states.getValue(i).health

    fun set(i: Integration, health: IntegrationHealth, detail: String? = null) {
        states[i] = IntegrationState(health, detail)
    }

    fun unavailable(i: Integration, why: String) = set(i, IntegrationHealth.UNAVAILABLE, why)

    fun healthy(i: Integration, detail: String? = null) = set(i, IntegrationHealth.HEALTHY, detail)

    fun success(i: Integration) {
        if (states.getValue(i).health == IntegrationHealth.FAILED) healthy(i)
    }

    fun failure(i: Integration, t: Throwable) {
        val detail = "${t.javaClass.simpleName}: ${t.message ?: "no message"}"
        set(i, IntegrationHealth.FAILED, detail)
        val now = System.currentTimeMillis()
        val prev = lastLogged[i]
        if (prev == null || now - prev > 60_000) {
            lastLogged[i] = now
            log("${i.label} failed ($detail). Continuing without it.")
        }
    }
}
