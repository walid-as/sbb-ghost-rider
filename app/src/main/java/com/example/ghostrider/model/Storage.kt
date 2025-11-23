package com.example.ghostrider.model

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.math.BigInteger
import org.json.JSONArray
import org.json.JSONObject

object Storage {

  private const val TAG = "Storage"
  private const val PREFS_NAME = "anon_ticket_prefs"
  private const val ENCRYPTED_PREFS_NAME = "anon_ticket_encrypted"
  private const val KEY_TICKETS = "tickets"

  private var encryptedPrefs: SharedPreferences? = null
  private var normalPrefs: SharedPreferences? = null

  fun init(context: Context) {
    val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()

    encryptedPrefs =
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    normalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun saveTicket(ticket: Ticket) {
    // Save secrets in encrypted storage
    encryptedPrefs?.edit()?.apply {
      putString("${ticket.ticketId}_nonce", ticket.nonce.joinToString(",") { it.toString() })
      putString("${ticket.ticketId}_acc", ticket.acc.toString(16))
      apply()
    }

    // Save public data in normal storage
    val tickets = getAllTickets().toMutableList()
    tickets.removeAll { it.ticketId == ticket.ticketId }
    tickets.add(ticket)

    val jsonArray = JSONArray()
    tickets.forEach { t ->
      val json =
          JSONObject().apply {
            put("ticketId", t.ticketId)
            put("P", t.P.toString(16))
            put("timestamp", t.timestamp)
            put("addedToWallet", t.addedToWallet)
            put("nfcEnabled", t.nfcEnabled)

            // Save all public keys
            val keysArray = JSONArray()
            t.publicKeys.forEach { key -> keysArray.put(key.toString(16)) }
            put("publicKeys", keysArray)
          }
      jsonArray.put(json)
    }

    normalPrefs?.edit()?.putString(KEY_TICKETS, jsonArray.toString())?.apply()
  }

  fun getAllTickets(): List<Ticket> {
    val ticketsJson = normalPrefs?.getString(KEY_TICKETS, "[]") ?: "[]"
    val jsonArray = JSONArray(ticketsJson)
    val tickets = mutableListOf<Ticket>()

    for (i in 0 until jsonArray.length()) {
      val json = jsonArray.getJSONObject(i)
      val ticketId = json.getString("ticketId")

      val nonceStr = encryptedPrefs?.getString("${ticketId}_nonce", null)
      val accStr = encryptedPrefs?.getString("${ticketId}_acc", null)

      if (nonceStr != null && accStr != null) {
        val nonce = nonceStr.split(",").map { it.toByte() }.toByteArray()
        val acc = BigInteger(accStr, 16)
        val P = BigInteger(json.getString("P"), 16)
        val timestamp = json.getLong("timestamp")
        val addedToWallet = json.getBoolean("addedToWallet")
        val nfcEnabled = json.optBoolean("nfcEnabled", false)

        // Load all public keys
        val publicKeys = mutableListOf<BigInteger>()
        val keysArray = json.optJSONArray("publicKeys")
        if (keysArray != null) {
          for (j in 0 until keysArray.length()) {
            publicKeys.add(BigInteger(keysArray.getString(j), 16))
          }
        } else {
          // Legacy: single key
          publicKeys.add(P)
        }

        tickets.add(
            Ticket(ticketId, nonce, acc, P, timestamp, addedToWallet, nfcEnabled, publicKeys)
        )
      }
    }

    return tickets
  }

  fun getTicket(ticketId: String): Ticket? {
    return getAllTickets().find { it.ticketId == ticketId }
  }

  fun markTicketInWallet(ticketId: String) {
    val ticket = getTicket(ticketId) ?: return
    saveTicket(ticket.copy(addedToWallet = true))
  }

  fun setNfcEnabled(ticketId: String, enabled: Boolean) {
    val tickets = getAllTickets()
    tickets.forEach { ticket ->
      if (ticket.ticketId == ticketId) {
        saveTicket(ticket.copy(nfcEnabled = enabled))
      } else if (enabled) {
        saveTicket(ticket.copy(nfcEnabled = false))
      }
    }
  }

  fun getNfcActiveTicket(): Ticket? {
    return getAllTickets().find { it.nfcEnabled }
  }

  /** Delete a ticket completely */
  fun deleteTicket(ticketId: String) {
    // Remove from encrypted storage
    encryptedPrefs?.edit()?.apply {
      remove("${ticketId}_nonce")
      remove("${ticketId}_acc")
      apply()
    }

    // Remove from normal storage
    val tickets = getAllTickets().filter { it.ticketId != ticketId }
    val jsonArray = JSONArray()
    tickets.forEach { t ->
      val json =
          JSONObject().apply {
            put("ticketId", t.ticketId)
            put("P", t.P.toString(16))
            put("timestamp", t.timestamp)
            put("addedToWallet", t.addedToWallet)
            put("nfcEnabled", t.nfcEnabled)

            val keysArray = JSONArray()
            t.publicKeys.forEach { key -> keysArray.put(key.toString(16)) }
            put("publicKeys", keysArray)
          }
      jsonArray.put(json)
    }

    normalPrefs?.edit()?.putString(KEY_TICKETS, jsonArray.toString())?.apply()
    Log.d(TAG, "Deleted ticket: $ticketId")
  }

  /** Export ticket with all registered public keys */
  fun exportTicket(ticketId: String): String {
    val ticket = getTicket(ticketId) ?: return ""
    return JSONObject()
        .apply {
          put("ticketId", ticket.ticketId)
          put("P", ticket.P.toString(16))
          put("timestamp", ticket.timestamp)

          val keysArray = JSONArray()
          ticket.publicKeys.forEach { key -> keysArray.put(key.toString(16)) }
          put("publicKeys", keysArray)
        }
        .toString()
  }

  /** Import ticket and register this device's public key */
  fun importTicket(jsonString: String): Boolean {
    return try {
      val json = JSONObject(jsonString)
      val ticketId = json.getString("ticketId")
      val timestamp = json.getLong("timestamp")

      // Parse existing public keys
      val existingPublicKeys = mutableListOf<BigInteger>()
      val publicKeysArray = json.optJSONArray("publicKeys")
      if (publicKeysArray != null) {
        for (i in 0 until publicKeysArray.length()) {
          existingPublicKeys.add(BigInteger(publicKeysArray.getString(i), 16))
        }
      } else {
        // Legacy format - single P
        val P = BigInteger(json.getString("P"), 16)
        existingPublicKeys.add(P)
      }

      // Derive THIS device's secret and public key
      val acc = CryptoHelper.deriveTicketSecret(ticketId)
      val nonce = CryptoHelper.generateNonce()
      val myP = CryptoHelper.computePublicKey(acc)

      // Add this device's public key to the list
      val allPublicKeys = existingPublicKeys.toMutableList()
      if (!allPublicKeys.contains(myP)) {
        allPublicKeys.add(myP)
        Log.d(TAG, "Added new device key to ticket. Total keys: ${allPublicKeys.size}")
      }

      val ticket =
          Ticket(
              ticketId = ticketId,
              nonce = nonce,
              acc = acc,
              P = myP,
              timestamp = timestamp,
              addedToWallet = false,
              nfcEnabled = false,
              publicKeys = allPublicKeys,
          )

      saveTicket(ticket)
      Log.d(TAG, "Imported ticket: $ticketId")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Import failed", e)
      false
    }
  }
}
