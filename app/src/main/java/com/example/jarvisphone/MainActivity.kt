package com.example.jarvisphone

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.jarvisphone.core.ChatMessage
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

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Jarvis Phone",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    StatusCard(
                        listening = uiState.isListening,
                        speaking = uiState.isSpeaking,
                        provider = uiState.lastProvider,
                        error = uiState.lastError
                    )

                    QuickActions(onAction = viewModel::onQuickAction)
                    ChatHistory(messages = uiState.messages)

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

    private fun requestNeededPermissions() {
        val required = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            add(Manifest.permission.CALL_PHONE)
            add(Manifest.permission.SEND_SMS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

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

@Composable
private fun QuickActions(onAction: (String) -> Unit) {
    val actions = listOf(
        "telegram och",
        "youtube och",
        "chrome och",
        "settings och",
        "qidir sun'iy intellekt",
        "clipboard get"
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Quick actions", fontWeight = FontWeight.SemiBold)
        actions.chunked(2).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                rowItems.forEach { item ->
                    AssistChip(
                        onClick = { onAction(item) },
                        label = { Text(item) }
                    )
                }
            }
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

    Surface(color = color, shape = RoundedCornerShape(16.dp)) {
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
