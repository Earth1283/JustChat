package io.github.Earth1283.justChat.core.config

enum class UnresolvedPolicy { KEEP, EMPTY }

data class ChatSection(val enabled: Boolean, val format: String)

data class PrivateMessageSection(val enabled: Boolean, val senderFormat: String, val receiverFormat: String)

data class MetadataDefaults(val prefix: String, val suffix: String, val group: String)

data class JustChatConfig(
    val chat: ChatSection,
    val privateMessages: PrivateMessageSection,
    val ignoreEnabled: Boolean,
    val colorsEnabled: Boolean,
    val unresolved: UnresolvedPolicy,
    val metadataDefaults: MetadataDefaults,
    val sqliteEnabled: Boolean,
    val debug: Boolean,
    val messages: Map<String, String>,
) {
    companion object {
        const val DEFAULT_CHAT_FORMAT = "<prefix><player> <dark_gray>»</dark_gray> <message>"
        const val DEFAULT_PM_SENDER = "<gray>[You → <receiver>]</gray> <message>"
        const val DEFAULT_PM_RECEIVER = "<gray>[<sender> → You]</gray> <message>"

        val DEFAULT = JustChatConfig(
            chat = ChatSection(enabled = true, format = DEFAULT_CHAT_FORMAT),
            privateMessages = PrivateMessageSection(true, DEFAULT_PM_SENDER, DEFAULT_PM_RECEIVER),
            ignoreEnabled = true,
            colorsEnabled = true,
            unresolved = UnresolvedPolicy.KEEP,
            metadataDefaults = MetadataDefaults(prefix = "", suffix = "", group = "default"),
            sqliteEnabled = true,
            debug = false,
            messages = Messages.DEFAULTS,
        )
    }
}

object Messages {
    val DEFAULTS: Map<String, String> = linkedMapOf(
        "no-permission" to "<red>You don't have permission to do that.",
        "players-only" to "<red>Only players can use this command.",
        "player-not-found" to "<red>No online player named <white><target></white>.",
        "usage-msg" to "<gray>Usage: <white>/msg <player> <message>",
        "usage-reply" to "<gray>Usage: <white>/reply <message>",
        "usage-ignore" to "<gray>Usage: <white>/ignore <player>",
        "usage-unignore" to "<gray>Usage: <white>/unignore <player>",
        "pm-disabled" to "<red>Private messages are disabled.",
        "pm-self" to "<red>You can't message yourself.",
        "pm-no-reply-target" to "<red>You have nobody to reply to.",
        "pm-you-ignore-target" to "<red>You are ignoring <white><target></white>. Use /unignore first.",
        "ignore-disabled" to "<red>Ignoring players is disabled.",
        "ignore-added" to "<gray>You are now ignoring <white><target></white>.",
        "ignore-removed" to "<gray>You are no longer ignoring <white><target></white>.",
        "ignore-already" to "<gray>You are already ignoring <white><target></white>.",
        "ignore-not-ignoring" to "<gray>You are not ignoring <white><target></white>.",
        "ignore-self" to "<red>You can't ignore yourself.",
        "ignore-exempt" to "<red>You can't ignore <white><target></white>.",
        "ignore-list-empty" to "<gray>You are not ignoring anyone.",
        "ignore-list" to "<gray>Ignoring: <white><list>",
        "color-usage" to "<gray>Usage: <white>/justchat color <color|#rrggbb|reset>",
        "color-disabled" to "<red>Chat colors are disabled.",
        "color-invalid" to "<red>Unknown color <white><value></white>. Use a color name or #rrggbb.",
        "color-set" to "<gray>Your chat color is now <chosen><chosen_name></chosen>.</gray>",
        "color-reset" to "<gray>Your chat color has been reset.",
        "reload-ok" to "<green>Configuration reloaded.",
        "reload-failed" to "<red>Reload failed; keeping the previous configuration. Run <white>/justchat doctor</white> for details.",
    )
}
