package com.example.jarvisphone.core

data class ChatMessage(
    val role: String,
    val text: String
)

data class AssistantUiState(
    val inputText: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val voiceEnabled: Boolean = false,
    val lastProvider: String? = null,
    val lastError: String? = null
)

sealed class CommandAction {
    data class OpenApp(val name: String) : CommandAction()
    data class OpenUrl(val url: String) : CommandAction()
    data class SearchWeb(val query: String) : CommandAction()
    data class SendSms(val number: String, val message: String) : CommandAction()
    data class Dial(val number: String) : CommandAction()
    data class Speak(val text: String) : CommandAction()
    data class Notify(val title: String, val text: String) : CommandAction()
    data class Toast(val text: String) : CommandAction()
    data class Vibrate(val durationMs: Long = 200) : CommandAction()
    data class ClipboardSet(val text: String) : CommandAction()
    data object ClipboardGet : CommandAction()
    data object OpenSettings : CommandAction()
    data object TermuxOpen : CommandAction()
}

data class AiPlan(
    val providerName: String,
    val reply: String,
    val actions: List<CommandAction>
)
