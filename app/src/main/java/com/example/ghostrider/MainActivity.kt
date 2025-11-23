package com.example.ghostrider

import android.Manifest
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.example.ghostrider.model.CryptoHelper
import com.example.ghostrider.model.Storage
import com.example.ghostrider.ui.ClientScreen
import com.example.ghostrider.ui.InspectorScreen

class MainActivity : ComponentActivity() {

  private val nfcPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions
        ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
          Toast.makeText(this, "✅ NFC permissions granted", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(
                  this,
                  "⚠️ NFC permissions denied. App may not work properly.",
                  Toast.LENGTH_LONG,
              )
              .show()
        }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Initialize storage
    try {
      Storage.init(applicationContext)
    } catch (e: Exception) {
      Toast.makeText(this, "Storage init error: ${e.message}", Toast.LENGTH_LONG).show()
    }

    // Initialize crypto
    try {
      CryptoHelper.getOrCreateDeviceKey()
    } catch (e: Exception) {
      Toast.makeText(this, "Crypto init error: ${e.message}", Toast.LENGTH_LONG).show()
    }

    // Check and request NFC permissions
    checkNfcPermissions()

    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation()
        }
      }
    }
  }

  private fun checkNfcPermissions() {
    val nfcAdapter = NfcAdapter.getDefaultAdapter(this)

    // Check if device has NFC hardware
    if (nfcAdapter == null) {
      Toast.makeText(this, "❌ NFC not supported on this device", Toast.LENGTH_LONG).show()
      return
    }

    // Check if NFC is enabled
    if (!nfcAdapter.isEnabled) {
      Toast.makeText(
              this,
              "⚠️ NFC is disabled. Enable it in Settings → Connected devices → NFC",
              Toast.LENGTH_LONG,
          )
          .show()
    }

    // Request runtime permissions for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val permissionsToRequest = mutableListOf<String>()

      if (
          ContextCompat.checkSelfPermission(this, Manifest.permission.NFC) !=
              PackageManager.PERMISSION_GRANTED
      ) {
        // Note: NFC permission is not dangerous, auto-granted
      }

      // Request location permissions (needed for challenge generation with GPS)
      if (
          ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
              PackageManager.PERMISSION_GRANTED
      ) {
        permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
      }

      if (permissionsToRequest.isNotEmpty()) {
        nfcPermissionLauncher.launch(permissionsToRequest.toTypedArray())
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
fun HomeScreen(navController: NavController) {
  Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }) { padding ->
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
