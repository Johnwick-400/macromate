package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.QwenLlm
import com.example.data.repository.QwenPhase
import com.example.ui.theme.*
import com.example.ui.viewmodel.MacroMateViewModel

/** Status + download control for the on-device Qwen model that powers the ReAct agent. */
@Composable
fun QwenModelCard(viewModel: MacroMateViewModel) {
    val state by viewModel.qwenState.collectAsState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("qwen_model_card"),
        colors = CardDefaults.cardColors(containerColor = SlateCard)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ON-DEVICE AI · QWEN 2.5",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NeonEmerald,
                letterSpacing = 1.2.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            when (state.phase) {
                QwenPhase.NOT_INSTALLED, QwenPhase.ERROR -> {
                    Text(
                        text = if (state.phase == QwenPhase.ERROR)
                            "Something went wrong: ${state.detail ?: "unknown error"}"
                        else
                            "Run the agent on ${QwenLlm.MODEL_LABEL} right on this phone: no API key, no internet when you ask. " +
                                "One-time download of about 1.6 GB. Use Wi-Fi and keep the app open until it finishes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.phase == QwenPhase.ERROR) SoftRed else TextSecondary
                    )
                    state.detail?.takeIf { state.phase == QwenPhase.NOT_INSTALLED }?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = TextHint)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.downloadQwenModel() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("qwen_download_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                    ) {
                        Text(
                            if (state.phase == QwenPhase.ERROR) "Try again" else "Download model",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                QwenPhase.DOWNLOADING -> {
                    Text(
                        text = "Downloading Qwen 2.5… ${state.percent}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonEmerald
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = { viewModel.cancelQwenDownload() }) {
                        Text("Cancel", color = TextSecondary)
                    }
                }

                QwenPhase.INSTALLED, QwenPhase.LOADING, QwenPhase.READY -> {
                    Text(
                        text = if (state.phase == QwenPhase.LOADING)
                            "Loading the model into memory…"
                        else
                            "Installed. The agent now plans and writes its replies with Qwen on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NeonEmerald
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.deleteQwenModel() },
                        modifier = Modifier.testTag("qwen_delete_button")
                    ) {
                        Text("Remove model (frees ~1.6 GB)", color = TextSecondary)
                    }
                }
            }
        }
    }
}
