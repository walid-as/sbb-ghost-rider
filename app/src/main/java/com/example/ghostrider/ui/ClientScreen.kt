package com.example.ghostrider.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.ghostrider.model.CryptoHelper
import com.example.ghostrider.model.Storage
import com.example.ghostrider.model.Ticket
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen() {
  val context = LocalContext.current
  var ticketsVersion by remember { mutableIntStateOf(0) }
  val tickets by remember(ticketsVersion) { derivedStateOf { Storage.getAllTickets() } }

  var showDemo by remember { mutableStateOf(false) }
  var demoResult by remember { mutableStateOf("") }
  var showImportDialog by remember { mutableStateOf(false) }
  var showDeleteDialog by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) { ticketsVersion++ }

  Scaffold(topBar = { TopAppBar(title = { Text("Client - My Tickets") }) }) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
              val nonce = CryptoHelper.generateNonce()
              val ticketId = CryptoHelper.computeTicketId(nonce)
              val acc = CryptoHelper.deriveTicketSecret(ticketId)
              val P = CryptoHelper.computePublicKey(acc)

              val ticket = Ticket(ticketId = ticketId, nonce = nonce, acc = acc, P = P)

              Storage.saveTicket(ticket)
              ticketsVersion++
            },
            modifier = Modifier.weight(1f),
        ) {
          Text("Generate")
        }

        OutlinedButton(onClick = { showImportDialog = true }, modifier = Modifier.weight(1f)) {
          Text("Import")
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Button(
          onClick = { showDemo = true },
          modifier = Modifier.fillMaxWidth(),
          colors =
              ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
      ) {
        Text("Run Local Demo (No NFC)")
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(text = "Wallet (${tickets.size} tickets)", style = MaterialTheme.typography.titleMedium)

      Spacer(modifier = Modifier.height(8.dp))

      if (tickets.isEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Box(
              modifier = Modifier.fillMaxWidth().padding(32.dp),
              contentAlignment = Alignment.Center,
          ) {
            Text(
                text = "No tickets yet.\nGenerate or Import a ticket to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
          items(items = tickets, key = { it.ticketId }) { ticket ->
            TicketCard(
                ticket = ticket,
                onAddToWallet = {
                  Storage.markTicketInWallet(ticket.ticketId)
                  ticketsVersion++
                },
                onNfcToggle = { enabled ->
                  Storage.setNfcEnabled(ticket.ticketId, enabled)
                  ticketsVersion++
                },
                onShare = {
                  val shareData = Storage.exportTicket(ticket.ticketId)
                  val shareIntent =
                      Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Anonymous Ticket")
                        putExtra(Intent.EXTRA_TEXT, shareData)
                      }
                  context.startActivity(Intent.createChooser(shareIntent, "Share Ticket"))
                },
                onDelete = { showDeleteDialog = ticket.ticketId },
            )
          }
        }
      }

      // Local Demo Dialog
      if (showDemo) {
        AlertDialog(
            onDismissRequest = { showDemo = false },
            title = { Text("Local Demo") },
            text = {
              Column {
                Text("Running full protocol locally without NFC...")
                Spacer(modifier = Modifier.height(8.dp))
                if (demoResult.isEmpty()) {
                  CircularProgressIndicator(modifier = Modifier.size(32.dp))
                  LaunchedEffect(Unit) { demoResult = runLocalDemo() }
                } else {
                  Text(demoResult)
                }
              }
            },
            confirmButton = {
              TextButton(
                  onClick = {
                    showDemo = false
                    demoResult = ""
                  }
              ) {
                Text("Close")
              }
            },
        )
      }

      // Import Dialog
      if (showImportDialog) {
        ImportTicketDialog(
            onDismiss = { showImportDialog = false },
            onImport = { jsonString ->
              val success = Storage.importTicket(jsonString)
              if (success) {
                ticketsVersion++
                Toast.makeText(context, "Ticket imported successfully!", Toast.LENGTH_SHORT).show()
              } else {
                Toast.makeText(context, "Import failed. Invalid ticket data.", Toast.LENGTH_LONG)
                    .show()
              }
              showImportDialog = false
            },
        )
      }

      // Delete Confirmation Dialog
      showDeleteDialog?.let { ticketId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Ticket?") },
            text = {
              Text("Are you sure you want to delete this ticket? This action cannot be undone.")
            },
            confirmButton = {
              Button(
                  onClick = {
                    Storage.deleteTicket(ticketId)
                    ticketsVersion++
                    showDeleteDialog = null
                    Toast.makeText(context, "Ticket deleted", Toast.LENGTH_SHORT).show()
                  },
                  colors =
                      ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
              ) {
                Text("Delete")
              }
            },
            dismissButton = {
              TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            },
        )
      }
    }
  }
}

@Composable
fun ImportTicketDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
  var ticketJson by remember { mutableStateOf("") }
  val context = LocalContext.current

  AlertDialog(
      onDismissRequest = onDismiss,
      title = { Text("Import Ticket") },
      text = {
        Column {
          Text(
              text = "Paste the ticket data you received:",
              style = MaterialTheme.typography.bodyMedium,
          )
          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
              value = ticketJson,
              onValueChange = { ticketJson = it },
              label = { Text("Ticket JSON") },
              placeholder = { Text("{\"ticketId\":\"...\", \"P\":\"...\", ...}") },
              modifier = Modifier.fillMaxWidth().height(150.dp),
              maxLines = 5,
          )

          Spacer(modifier = Modifier.height(8.dp))

          TextButton(
              onClick = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                  val text = clipData.getItemAt(0).text
                  if (text != null) {
                    ticketJson = text.toString()
                  }
                }
              }
          ) {
            Text("Paste from Clipboard")
          }

          Spacer(modifier = Modifier.height(8.dp))

          Text(
              text =
                  "✓ Imported tickets work with NFC\n✓ Your device key will be added to the ticket",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary,
          )
        }
      },
      confirmButton = {
        Button(onClick = { onImport(ticketJson) }, enabled = ticketJson.isNotBlank()) {
          Text("Import")
        }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}

@Composable
fun TicketCard(
    ticket: Ticket,
    onAddToWallet: () -> Unit,
    onNfcToggle: (Boolean) -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
  val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
  val hasMultipleKeys = ticket.publicKeys.size > 1

  Card(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = "Ticket ID: ${ticket.ticketId.take(16)}...",
              style = MaterialTheme.typography.bodyMedium,
          )
          Text(
              text = "Created: ${dateFormat.format(Date(ticket.timestamp))}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row {
          IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "Share") }
          IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      if (ticket.addedToWallet) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color =
                if (ticket.nfcEnabled) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium,
        ) {
          Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                  text = if (ticket.nfcEnabled) "📡 NFC ACTIVE - Ready to scan" else "✓ In Wallet",
                  color =
                      if (ticket.nfcEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodyMedium,
              )
            }

            Switch(checked = ticket.nfcEnabled, onCheckedChange = onNfcToggle)
          }
        }
      } else {
        Button(onClick = onAddToWallet, modifier = Modifier.fillMaxWidth()) {
          Text("Add to Wallet")
        }
        Text(
            text =
                if (hasMultipleKeys) "Multi-device ticket. Add to wallet to enable NFC."
                else "Add to wallet to enable NFC",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}

fun runLocalDemo(): String {
  val tickets = Storage.getAllTickets()
  if (tickets.isEmpty()) {
    return "No tickets available. Generate a ticket first."
  }

  val ticket = tickets.first()
  val timestamp = System.currentTimeMillis()
  val challenge = CryptoHelper.computeChallenge(timestamp, 0.0, 0.0)
  val (S, R) = CryptoHelper.generateSchnorrSignature(ticket.acc, challenge)
  val isValid = CryptoHelper.verifySchnorrSignature(ticket.P, challenge, S, R)

  return """
        Demo Complete!
        
        Ticket: ${ticket.ticketId.take(16)}...
        Registered Keys: ${ticket.publicKeys.size}
        Challenge: ${challenge.toString(16).take(16)}...
        S: ${S.toString(16).take(16)}...
        R: ${R.toString(16).take(16)}...
        
        Verification: ${if (isValid) "✓ VALID" else "✗ INVALID"}
        
        This proves the Schnorr signature scheme works correctly.
    """
      .trimIndent()
}
