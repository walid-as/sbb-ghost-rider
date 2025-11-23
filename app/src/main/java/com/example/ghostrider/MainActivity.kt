package com.example.ghostrider

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.example.ghostrider.ui.ClientScreen
import com.example.anonticketdemo.InspectorScreen
import com.example.ghostrider.model.CryptoHelper
import com.example.ghostrider.model.Storage

class MainActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize storage
    Storage.init(applicationContext)

    // Initialize crypto params (just to ensure they're loaded)
    CryptoHelper.getOrCreateDeviceKey()

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
        }
      }
    }
  }
}

@Composable
fun MainNavigation() {
  val navController = rememberNavController()

  NavHost(navController = navController, startDestination = "home") {
    composable("home") { HomeScreen(navController) }
    composable("client") { ClientScreen() }
    composable("inspector") { InspectorScreen() }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: androidx.navigation.NavController) {
  Scaffold(topBar = { TopAppBar(title = { Text("AnonTicketDemo") }) }) { padding ->
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
          text = "Anonymous Ticket System",
          style = MaterialTheme.typography.headlineMedium,
          modifier = Modifier.padding(bottom = 32.dp),
      )

      Button(
          onClick = { navController.navigate("client") },
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      ) {
        Text("CLIENT MODE", modifier = Modifier.padding(8.dp))
      }

      Button(
          onClick = { navController.navigate("inspector") },
          modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
      ) {
        Text("INSPECTOR MODE", modifier = Modifier.padding(8.dp))
      }

      Spacer(modifier = Modifier.height(32.dp))

      Text(
          text = "CLIENT: Generate and use tickets\nINSPECTOR: Verify tickets via NFC",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
