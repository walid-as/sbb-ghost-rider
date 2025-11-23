package com.example.ghostrider.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ghostrider.model.NfcReader
import com.example.ghostrider.model.VerificationResult
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorScreen() {
  val context = LocalContext.current
  val activity = context as Activity

  var isReading by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf("Tap 'Start NFC Reader' to begin") }
  var showVerificationDialog by remember { mutableStateOf(false) }
  var currentResult by remember { mutableStateOf<VerificationResult?>(null) }

  var nfcReader by remember { mutableStateOf<NfcReader?>(null) }

  DisposableEffect(Unit) { onDispose { nfcReader?.disable() } }

  Scaffold(topBar = { TopAppBar(title = { Text("Inspector - Verify Tickets") }) }) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = if (isReading) "🔍 NFC Reader Active" else "NFC Reader Inactive",
          style = MaterialTheme.typography.headlineSmall,
          color =
              if (isReading) MaterialTheme.colorScheme.primary
              else MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(16.dp))

      Button(
          onClick = {
            if (isReading) {
              nfcReader?.disable()
              nfcReader = null
              isReading = false
              statusMessage = "Reader stopped"
            } else {
              nfcReader =
                  NfcReader(
                      activity = activity,
                      onResult = { result ->
                        currentResult = result
                        showVerificationDialog = true
                        statusMessage =
                            if (result.isValid) {
                              "✓ Ticket VALID"
                            } else {
                              "✗ Ticket INVALID"
                            }
                      },
                      onError = { error -> statusMessage = "Error: $error" },
                  )
              nfcReader?.enable()
              isReading = true
              statusMessage = "Hold phone near client device..."
            }
          },
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text(if (isReading) "Stop NFC Reader" else "Start NFC Reader")
      }

      Spacer(modifier = Modifier.height(24.dp))

      Card(
          modifier = Modifier.fillMaxWidth(),
          colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(text = "Status", style = MaterialTheme.typography.titleMedium)
          Spacer(modifier = Modifier.height(8.dp))
          Text(text = statusMessage)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
          text =
              """
              Instructions:
              1. Ensure both devices have NFC enabled
              2. Client enables NFC toggle on a ticket
              3. Inspector taps "Start NFC Reader"
              4. Hold devices back-to-back
              5. Verification popup appears automatically
              """
                  .trimIndent(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    // Verification Result Dialog
    if (showVerificationDialog && currentResult != null) {
      VerificationDialog(result = currentResult!!, onDismiss = { showVerificationDialog = false })
    }
  }
}

@Composable
fun VerificationDialog(result: VerificationResult, onDismiss: () -> Unit) {
  val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault()) }

  AlertDialog(
      onDismissRequest = onDismiss,
      containerColor =
          if (result.isValid) {
            MaterialTheme.colorScheme.primaryContainer
          } else {
            MaterialTheme.colorScheme.errorContainer
          },
      title = {
        Text(
            text = if (result.isValid) "✓ VALID TICKET" else "✗ INVALID TICKET",
            style = MaterialTheme.typography.headlineMedium,
            color =
                if (result.isValid) {
                  MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                  MaterialTheme.colorScheme.onErrorContainer
                },
        )
      },
      text = {
        Column {
          Text("Ticket ID: ${result.ticketId.take(16)}...")
          Spacer(modifier = Modifier.height(4.dp))
          Text("Challenge: ${result.challenge}")
          Spacer(modifier = Modifier.height(4.dp))
          Text("Location: ${result.location}")
          Spacer(modifier = Modifier.height(4.dp))
          Text("Time: ${dateFormat.format(Date(result.timestamp))}")
        }
      },
      confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
  )
}
