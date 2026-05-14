package com.example.jarvisphone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.jarvisphone.core.*
import com.example.jarvisphone.ui.AppTheme
import com.example.jarvisphone.vm.AssistantViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            viewModel.onPermissionsResult(grants)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNeededPermissions()

        setContent {
            AppTheme {
                val uiState by viewModel.uiState.collectAsState()

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Jarvis Phone") }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatusCard(
                            listening = uiState.isListening,
                            speaking = uiState.isSpeaking,
                            provider = uiState.lastProvider,
                            error = uiState.lastError
                        )

                        QuickActions(
                            onAction = viewModel::onQuickAction
                        )

                        ChatHistory(
                            messages = uiState.messages
                        )

                        AssistantInput(
                            text = uiState.inputText,
                            onTextChange = viewModel::onInputTextChange,
                            onSend = { viewModel.submitText(uiState.inputText) },
                            onMic = { viewModel.toggleVoice() },
                            voiceEnabled = uiState.voiceEnabled
                        )
                    }
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val required = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.POST_NOTIFICATIONS
        )

        val missing = required.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }
}

@Composable
private fun StatusCard(
    listening: Boolean,
    speaking: Boolean,
    provider: String?,
    error: String?
) {
    ElevatedCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Status", fontWeight = FontWeight.Bold)
            Text(
                text = when {
                    speaking -> "Speaking"
                    listening -> "Listening"
                    else -> "Idle"
                }
            )
            Text("Provider: ${provider ?: "local"}")
            if (!error.isNullOrBlank()) {
                Text("Error: $error", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(onAction: (String) -> Unit) {
    val actions = listOf(
        "telegram och",
        "youtube och",
        "chrome och",
        "settings och",
        "qidir sun'iy intellekt",
        "battery",
        "clipboard get"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick actions", fontWeight = FontWeight.SemiBold)
        FlowRowWithChips(actions, onAction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlowRowWithChips(items: List<String>, onAction: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            AssistChip(
                onClick = { onAction(item) },
                label = { Text(item) }
            )
        }
    }
}

@Composable
private fun ChatHistory(messages: List<ChatMessage>) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { msg ->
                MessageBubble(msg)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val color = if (message.role == "user") {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(message.role.uppercase(), style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(4.dp))
            Text(message.text)
        }
    }
}

@Composable
private fun AssistantInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    voiceEnabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Command") },
            placeholder = { Text("telegram och, sms yoz, youtube och...") },
            singleLine = false,
            minLines = 2
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            OutlinedButton(onClick = onMic) {
                Text(if (voiceEnabled) "Mic On" else "Mic Off")
            }
            Button(onClick = onSend) {
                Text("Send")
            }
        }
    }
}
