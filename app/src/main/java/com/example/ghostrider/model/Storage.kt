package com.example.ghostrider.model

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger

object Storage {

    private const val PREFS_NAME = "anon_ticket_prefs"
    private const val ENCRYPTED_PREFS_NAME = "anon_ticket_encrypted"
    private const val KEY_TICKETS = "tickets"

    private var encryptedPrefs: SharedPreferences? = null
    private var normalPrefs: SharedPreferences? = null

    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        normalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Save a ticket. Secrets (nonce, acc) stored in encrypted prefs.
     * Public data (ticketId, P) in normal prefs.
     */
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
            val json = JSONObject().apply {
                put("ticketId", t.ticketId)
                put("P", t.P.toString(16))
                put("timestamp", t.timestamp)
                put("addedToWallet", t.addedToWallet)
            }
            jsonArray.put(json)
        }

        normalPrefs?.edit()?.putString(KEY_TICKETS, jsonArray.toString())?.apply()
    }

    /**
     * Retrieve all tickets (reconstructing from both storage types)
     */
    fun getAllTickets(): List<Ticket> {
        val ticketsJson = normalPrefs?.getString(KEY_TICKETS, "[]") ?: "[]"
        val jsonArray = JSONArray(ticketsJson)
        val tickets = mutableListOf<Ticket>()

        for (i in 0 until jsonArray.length()) {
            val json = jsonArray.getJSONObject(i)
            val ticketId = json.getString("ticketId")

            // Retrieve secrets from encrypted storage
            val nonceStr = encryptedPrefs?.getString("${ticketId}_nonce", null)
            val accStr = encryptedPrefs?.getString("${ticketId}_acc", null)

            if (nonceStr != null && accStr != null) {
                val nonce = nonceStr.split(",").map { it.toByte() }.toByteArray()
                val acc = BigInteger(accStr, 16)
                val P = BigInteger(json.getString("P"), 16)
                val timestamp = json.getLong("timestamp")
                val addedToWallet = json.getBoolean("addedToWallet")

                tickets.add(Ticket(ticketId, nonce, acc, P, timestamp, addedToWallet))
            }
        }

        return tickets
    }

    /**
     * Get a specific ticket by ID
     */
    fun getTicket(ticketId: String): Ticket? {
        return getAllTickets().find { it.ticketId == ticketId }
    }

    /**
     * Mark ticket as added to wallet
     */
    fun markTicketInWallet(ticketId: String) {
        val ticket = getTicket(ticketId) ?: return
        saveTicket(ticket.copy(addedToWallet = true))
    }

    /**
     * Get the current active ticket (most recent added to wallet)
     * Used by HCE service to know which ticket to present
     */
    fun getActiveTicket(): Ticket? {
        return getAllTickets()
            .filter { it.addedToWallet }
            .maxByOrNull { it.timestamp }
    }
}
