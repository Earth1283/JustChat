package io.github.Earth1283.justChat.core.config

class ConfigParseResult(
    val config: JustChatConfig?,
    val errors: List<String>,
    val warnings: List<String>,
)

object ConfigParser {
    private val SCALAR_KEYS = setOf(
        "config-version",
        "debug",
        "chat.enabled", "chat.format",
        "private-messages.enabled", "private-messages.sender-format", "private-messages.receiver-format",
        "ignore.enabled",
        "colors.enabled",
        "placeholders.unresolved",
        "metadata.default-prefix", "metadata.default-suffix", "metadata.default-group",
        "continuity.sqlite",
    )
    private val MESSAGE_KEYS = Messages.DEFAULTS.keys.map { "messages.$it" }.toSet()
    private val KNOWN_KEYS = SCALAR_KEYS + MESSAGE_KEYS

    fun parse(flat: Map<String, Any?>): ConfigParseResult {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        val d = JustChatConfig.DEFAULT

        for (key in flat.keys) {
            if (key !in KNOWN_KEYS) warnings += "Unknown config key '$key' (ignored)."
        }

        fun bool(path: String, default: Boolean): Boolean = when (val v = flat[path]) {
            null -> default
            is Boolean -> v
            else -> { errors += "'$path' must be true or false, got '$v'."; default }
        }

        fun string(path: String, default: String): String = when (val v = flat[path]) {
            null -> default
            is String -> v
            is Number, is Boolean -> v.toString()
            else -> { errors += "'$path' must be text, got '$v'."; default }
        }

        fun format(path: String, default: String): String {
            val s = string(path, default)
            if (s.isBlank()) {
                errors += "'$path' must not be empty."
                return default
            }
            if (!s.contains("<message>")) {
                errors += "'$path' must contain <message>, otherwise nobody could read the message."
                return default
            }
            return s
        }

        val unresolved = when (val v = flat["placeholders.unresolved"]) {
            null -> d.unresolved
            is String -> UnresolvedPolicy.entries.firstOrNull { it.name.equals(v.trim(), ignoreCase = true) }
                ?: run { errors += "'placeholders.unresolved' must be KEEP or EMPTY, got '$v'."; d.unresolved }
            else -> { errors += "'placeholders.unresolved' must be KEEP or EMPTY, got '$v'."; d.unresolved }
        }

        val messages = LinkedHashMap(Messages.DEFAULTS)
        for (key in Messages.DEFAULTS.keys) {
            val v = flat["messages.$key"] ?: continue
            if (v is String) messages[key] = v else errors += "'messages.$key' must be text, got '$v'."
        }

        val config = JustChatConfig(
            chat = ChatSection(
                enabled = bool("chat.enabled", d.chat.enabled),
                format = format("chat.format", d.chat.format),
            ),
            privateMessages = PrivateMessageSection(
                enabled = bool("private-messages.enabled", d.privateMessages.enabled),
                senderFormat = format("private-messages.sender-format", d.privateMessages.senderFormat),
                receiverFormat = format("private-messages.receiver-format", d.privateMessages.receiverFormat),
            ),
            ignoreEnabled = bool("ignore.enabled", d.ignoreEnabled),
            colorsEnabled = bool("colors.enabled", d.colorsEnabled),
            unresolved = unresolved,
            metadataDefaults = MetadataDefaults(
                prefix = string("metadata.default-prefix", d.metadataDefaults.prefix),
                suffix = string("metadata.default-suffix", d.metadataDefaults.suffix),
                group = string("metadata.default-group", d.metadataDefaults.group),
            ),
            sqliteEnabled = bool("continuity.sqlite", d.sqliteEnabled),
            debug = bool("debug", d.debug),
            messages = messages,
        )
        return ConfigParseResult(if (errors.isEmpty()) config else null, errors, warnings)
    }
}
