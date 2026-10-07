package io.github.Earth1283.justChat.core.config

import io.github.Earth1283.justChat.core.format.TemplateException

enum class ConfigState { LOADED, RETAINED_PREVIOUS, DEFAULTS_ONLY }

class ConfigStatus(
    val state: ConfigState,
    val errors: List<String>,
    val warnings: List<String>,
)

class ReloadOutcome(val applied: Boolean, val status: ConfigStatus)

class ConfigManager {
    @Volatile
    var current: RuntimeConfig = RuntimeConfig.compile(JustChatConfig.DEFAULT)
        private set

    @Volatile
    var status: ConfigStatus = ConfigStatus(ConfigState.DEFAULTS_ONLY, emptyList(), emptyList())
        private set

    private var hasLoadedValidConfig = false

    @Synchronized
    fun reload(readFlatConfig: () -> Map<String, Any?>): ReloadOutcome {
        val flat = try {
            readFlatConfig()
        } catch (t: Throwable) {
            return reject(listOf("Config file could not be read: ${t.message ?: t.javaClass.simpleName}"), emptyList())
        }

        val parsed = ConfigParser.parse(flat)
        val config = parsed.config ?: return reject(parsed.errors, parsed.warnings)

        val compiled = try {
            RuntimeConfig.compile(config)
        } catch (e: TemplateException) {
            return reject(parsed.errors + "Format error: ${e.message}", parsed.warnings)
        }

        current = compiled
        hasLoadedValidConfig = true
        status = ConfigStatus(ConfigState.LOADED, emptyList(), parsed.warnings)
        return ReloadOutcome(applied = true, status = status)
    }

    private fun reject(errors: List<String>, warnings: List<String>): ReloadOutcome {
        val state = if (hasLoadedValidConfig) ConfigState.RETAINED_PREVIOUS else ConfigState.DEFAULTS_ONLY
        status = ConfigStatus(state, errors, warnings)
        return ReloadOutcome(applied = false, status = status)
    }
}
