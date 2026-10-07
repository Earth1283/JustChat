package io.github.Earth1283.justChat.core.config

import io.github.Earth1283.justChat.core.format.CompiledTemplate
import io.github.Earth1283.justChat.core.format.Placeholder

class RuntimeConfig private constructor(
    val config: JustChatConfig,
    val chatTemplate: CompiledTemplate,
    val privateSenderTemplate: CompiledTemplate,
    val privateReceiverTemplate: CompiledTemplate,
) {
    val usesExternalPlaceholders: Boolean
        get() = chatTemplate.usesExternal || privateSenderTemplate.usesExternal || privateReceiverTemplate.usesExternal

    companion object {
        fun compile(config: JustChatConfig) = RuntimeConfig(
            config = config,
            chatTemplate = CompiledTemplate.compile(config.chat.format, Placeholder.CHAT),
            privateSenderTemplate = CompiledTemplate.compile(config.privateMessages.senderFormat, Placeholder.PRIVATE_MESSAGE),
            privateReceiverTemplate = CompiledTemplate.compile(config.privateMessages.receiverFormat, Placeholder.PRIVATE_MESSAGE),
        )
    }
}
