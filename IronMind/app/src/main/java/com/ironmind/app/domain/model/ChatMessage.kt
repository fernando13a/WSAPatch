package com.ironmind.app.domain.model

/** Who wrote a chat turn. */
enum class ChatAuthor { USER, COACH }

/**
 * One turn in the on-device coach conversation. [isStreaming] marks the coach bubble that tokens
 * are still landing in (the typewriter effect), and [isError] marks a turn whose generation
 * failed so the UI can offer a retry instead of treating the text as coaching advice.
 */
data class ChatMessage(
    val id: Long,
    val author: ChatAuthor,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false,
)
