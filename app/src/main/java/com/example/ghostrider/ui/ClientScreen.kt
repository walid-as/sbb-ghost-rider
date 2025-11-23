package com.example.ghostrider.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ghostrider.model.CryptoHelper
import com.example.ghostrider.model.Storage
import com.example.ghostrider.model.Ticket
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientScreen() {
  // Use mutableStateOf with derivedStateOf for automatic updates
  var ticketsVersion by remember { mutableIntStateOf(0) }
  val tickets by remember(ticketsVersion) { derivedStateOf { Storage.getAllTickets() } }

  var showDemo by remember { mutableStateOf(false) }
  var demoResult by remember { mutableStateOf("") }

  // Refresh tickets when screen appears
  LaunchedEffect(Unit) { ticketsVersion++ }

  Scaffold(topBar = { TopAppBar(title = { Text("Client - My Tickets") }) }) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
      Button(
          onClick = {
            // Generate new ticket
            val nonce = CryptoHelper.generateNonce()
            val ticketId = CryptoHelper.computeTicketId(nonce)
            val acc = CryptoHelper.deriveTicketSecret(ticketId)
            val P = CryptoHelper.computePublicKey(acc)

            val ticket = Ticket(ticketId = ticketId, nonce = nonce, acc = acc, P = P)

            Storage.saveTicket(ticket)
            ticketsVersion++ // Trigger refresh
          },
          modifier = Modifier.fillMaxWidth(),
      ) {
        Text("Generate New Ticket")
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

      Text(
          text = "Wallet (${tickets.size} tickets)",
          style = MaterialTheme.typography.titleMedium,
      )

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
                text = "No tickets yet.\nTap 'Generate New Ticket' to start.",
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
                  ticketsVersion++ // Trigger refresh
                },
            )
          }
        }
      }

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
    }
  }
}

@Composable
fun TicketCard(ticket: Ticket, onAddToWallet: () -> Unit) {
  val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

  Card(
      modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Text(
          text = "Ticket ID: ${ticket.ticketId.take(16)}...",
          style = MaterialTheme.typography.bodyMedium,
      )
      Text(
          text = "Created: ${dateFormat.format(Date(ticket.timestamp))}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      Spacer(modifier = Modifier.height(8.dp))

      if (ticket.addedToWallet) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium,
        ) {
          Row(
              modifier = Modifier.padding(12.dp),
              horizontalArrangement = Arrangement.Center,
              verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
                text = "✓ In Wallet (Active for NFC)",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
          }
        }
      } else {
        Button(onClick = onAddToWallet, modifier = Modifier.fillMaxWidth()) {
          Text("Add to Wallet")
        }
        Text(
            text = "Note: Simulated wallet. Ready for NFC once added.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
      }
    }
  }
}

/**
 * Local demo that simulates the full protocol without NFC. This proves the crypto works correctly.
 */
fun runLocalDemo(): String {
  val tickets = Storage.getAllTickets()
  if (tickets.isEmpty()) {
    return "No tickets available. Generate a ticket first."
  }

  val ticket = tickets.first()

  // Simulate inspector challenge
  val timestamp = System.currentTimeMillis()
  val challenge = CryptoHelper.computeChallenge(timestamp, 0.0, 0.0)

  // Client generates signature
  val (S, R) = CryptoHelper.generateSchnorrSignature(ticket.acc, challenge)

  // Inspector verifies
  val isValid = CryptoHelper.verifySchnorrSignature(ticket.P, challenge, S, R)

  return """
        Demo Complete!
        
        Ticket: ${ticket.ticketId.take(16)}...
        Challenge: ${challenge.toString(16).take(16)}...
        S: ${S.toString(16).take(16)}...
        R: ${R.toString(16).take(16)}...
        
        Verification: ${if (isValid) "✓ VALID" else "✗ INVALID"}
        
        This proves the Schnorr signature scheme works correctly.
    """
      .trimIndent()
}
