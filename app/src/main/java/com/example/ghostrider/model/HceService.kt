package com.example.ghostrider.model

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.math.BigInteger

/**
 * HCE Service that responds to Inspector NFC reads.
 *
 * Protocol:
 * 1. Inspector sends SELECT AID (F0010203040506)
 * 2. Inspector sends INS=0xA1 -> Service responds with ticketId
 * 3. Inspector sends INS=0xA2 with challenge c (32 bytes) -> Service responds with (S, R)
 */
class HceService : HostApduService() {

    companion object {
        private const val TAG = "HceService"
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06
        )
        private val SELECT_APDU_HEADER = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00
        )
        private const val INS_GET_TICKET_ID: Byte = 0xA1.toByte()
        private const val INS_CHALLENGE: Byte = 0xA2.toByte()
        private val SUCCESS = byteArrayOf(0x90.toByte(), 0x00)
        private val FAILURE = byteArrayOf(0x6F.toByte(), 0x00)
    }

    private var currentTicket: Ticket? = null

    override fun onCreate() {
        super.onCreate()
        Storage.init(applicationContext)
        Log.d(TAG, "HceService created")
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        Log.d(TAG, "Received APDU: ${commandApdu.joinToString(" ") { "%02X".format(it) }}")

        // Handle SELECT AID
        if (commandApdu.size >= 5 && isSelectAidApdu(commandApdu)) {
            Log.d(TAG, "SELECT AID received")
            currentTicket = Storage.getActiveTicket()
            if (currentTicket == null) {
                Log.w(TAG, "No active ticket in wallet")
                return FAILURE
            }
            Log.d(TAG, "Active ticket: ${currentTicket?.ticketId}")
            return SUCCESS
        }

        // Handle GET TICKET ID (INS=0xA1)
        if (commandApdu.size >= 4 && commandApdu[1] == INS_GET_TICKET_ID) {
            Log.d(TAG, "GET_TICKET_ID request")
            val ticket = currentTicket ?: return FAILURE
            val ticketIdBytes = ticket.ticketId.toByteArray()
            return ticketIdBytes + SUCCESS
        }

        // Handle CHALLENGE (INS=0xA2)
        if (commandApdu.size >= 37 && commandApdu[1] == INS_CHALLENGE) {
            Log.d(TAG, "CHALLENGE request")
            val ticket = currentTicket ?: return FAILURE

            // Extract challenge (32 bytes starting at index 5)
            val challengeBytes = commandApdu.copyOfRange(5, 37)
            val challenge = BigInteger(1, challengeBytes)

            Log.d(TAG, "Challenge: ${challenge.toString(16)}")

            // Generate Schnorr signature
            val (S, R) = CryptoHelper.generateSchnorrSignature(ticket.acc, challenge)

            // Serialize (S, R) as TLV: len(S)|S|len(R)|R
            val sBytes = S.toByteArray()
            val rBytes = R.toByteArray()

            val response = ByteArray(4 + sBytes.size + rBytes.size)
            var offset = 0

            // S length (2 bytes)
            response[offset++] = ((sBytes.size shr 8) and 0xFF).toByte()
            response[offset++] = (sBytes.size and 0xFF).toByte()
            // S data
            System.arraycopy(sBytes, 0, response, offset, sBytes.size)
            offset += sBytes.size

            // R length (2 bytes)
            response[offset++] = ((rBytes.size shr 8) and 0xFF).toByte()
            response[offset++] = (rBytes.size and 0xFF).toByte()
            // R data
            System.arraycopy(rBytes, 0, response, offset, rBytes.size)

            Log.d(TAG, "Sending signature response (${response.size + 2} bytes)")
            return response + SUCCESS
        }

        Log.w(TAG, "Unknown APDU command")
        return FAILURE
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "Deactivated: $reason")
        currentTicket = null
    }

    private fun isSelectAidApdu(apdu: ByteArray): Boolean {
        if (apdu.size < SELECT_APDU_HEADER.size + AID.size) return false
        for (i in SELECT_APDU_HEADER.indices) {
            if (apdu[i] != SELECT_APDU_HEADER[i]) return false
        }
        val aidLength = apdu[4].toInt() and 0xFF
        if (aidLength != AID.size) return false
        for (i in AID.indices) {
            if (apdu[5 + i] != AID[i]) return false
        }
        return true
    }
}
