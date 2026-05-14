package com.example.jarvisphone.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.jarvisphone.ai.AiPlanner
import com.example.jarvisphone.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AssistantViewModel(app: Application) : AndroidViewModel(app) {

    private val appContext = app.applicationContext
    private val commandParser = CommandParser(appContext)
    private val appLauncher = AppLauncher(appContext)
    private val voiceController = VoiceController(appContext)
    private val aiPlanner = AiPlanner(appContext)

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        voiceController.onResult = { text ->
            submitText(text)
        }
        voiceController.onSpeaking = { speaking ->
            _uiState.value = _uiState.value.copy(isSpeaking = speaking)
        }
        voiceController.onListening = { listening ->
            _uiState.value = _uiState.value.copy(isListening = listening)
        }
        voiceController.onError = { error ->
            pushSystem("Voice error: $error")
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.value = _uiState.value.copy(inputText = text)
    }

    fun toggleVoice() {
        if (_uiState.value.voiceEnabled) {
            voiceController.stopListening()
            _uiState.value = _uiState.value.copy(voiceEnabled = false, isListening = false)
        } else {
            voiceController.startListening()
            _uiState.value = _uiState.value.copy(voiceEnabled = true)
        }
    }

    fun onPermissionsResult(grants: Map<String, Boolean>) {
        val denied = grants.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            pushSystem("Some permissions denied: ${denied.joinToString()}")
        }
    }

    fun onQuickAction(text: String) {
        submitText(text)
    }

    fun submitText(text: String) {
        val input = text.trim()
        if (input.isBlank()) return

        appendMessage("user", input)
        _uiState.value = _uiState.value.copy(inputText = "")

        viewModelScope.launch {
            val local = commandParser.parse(input)
            if (local != null) {
                executeAction(local)
                return@launch
            }

            val plan = aiPlanner.plan(input, _uiState.value.messages)
            if (plan != null) {
                _uiState.value = _uiState.value.copy(lastProvider = plan.providerName)
                if (plan.reply.isNotBlank()) {
                    speakAndLog(plan.reply)
                }
                plan.actions.forEach { action ->
                    executeAction(action)
                }
            } else {
                speakAndLog("AI javob bermadi. Buyruqni aniqroq yozing.")
            }
        }
    }

    private suspend fun executeAction(action: CommandAction) {
        when (action) {
            is CommandAction.OpenApp -> {
                val ok = appLauncher.openApp(action.name)
                pushSystem(if (ok) "${action.name} opened" else "${action.name} not found")
            }
            is CommandAction.OpenUrl -> {
                val ok = appLauncher.openUrl(action.url)
                pushSystem(if (ok) "URL opened" else "URL failed")
            }
            is CommandAction.SearchWeb -> {
                val ok = appLauncher.searchWeb(action.query)
                pushSystem(if (ok) "Search opened" else "Search failed")
            }
            is CommandAction.Dial -> {
                val ok = appLauncher.dial(action.number)
                pushSystem(if (ok) "Dial opened" else "Dial failed")
            }
            is CommandAction.SendSms -> {
                val ok = appLauncher.sendSms(action.number, action.message)
                pushSystem(if (ok) "SMS composer opened/sent" else "SMS failed")
            }
            is CommandAction.Speak -> speakAndLog(action.text)
            is CommandAction.Notify -> appLauncher.notify(action.title, action.text)
            is CommandAction.Toast -> appLauncher.toast(action.text)
            is CommandAction.Vibrate -> appLauncher.vibrate(action.durationMs)
            is CommandAction.ClipboardSet -> appLauncher.setClipboard(action.text)
            is CommandAction.ClipboardGet -> {
                val clip = appLauncher.getClipboard()
                speakAndLog("Clipboard: ${clip.ifBlank { "bo'sh" }}")
            }
            is CommandAction.OpenSettings -> {
                val ok = appLauncher.openSettings()
                pushSystem(if (ok) "Settings opened" else "Settings failed")
            }
            is CommandAction.TermuxOpen -> {
                val ok = appLauncher.openTermux()
                pushSystem(if (ok) "Termux opened" else "Termux not found")
            }
        }
    }

    private fun appendMessage(role: String, text: String) {
        val updated = _uiState.value.messages + ChatMessage(role = role, text = text)
        _uiState.value = _uiState.value.copy(messages = updated)
    }

    private fun pushSystem(text: String) {
        appendMessage("system", text)
    }

    private fun speakAndLog(text: String) {
        appendMessage("assistant", text)
        voiceController.speak(text)
    }
}
