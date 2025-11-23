package com.example.ghostrider.model

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.math.BigInteger

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

        if (commandApdu.size >= 5 && isSelectAidApdu(commandApdu)) {
            Log.d(TAG, "SELECT AID received")
            currentTicket = Storage.getNfcActiveTicket() // ← Use NFC-enabled ticket
            if (currentTicket == null) {
                Log.w(TAG, "No ticket with NFC enabled")
                return FAILURE
            }
            Log.d(TAG, "Active NFC ticket: ${currentTicket?.ticketId}")
            return SUCCESS
        }

        // After: send ticketId length, ticketId bytes, P length, P bytes
        if (commandApdu.size >= 4 && commandApdu[1] == INS_GET_TICKET_ID) {
            Log.d(TAG, "GET_TICKET_ID request")
            val ticket = currentTicket ?: return FAILURE

            val ticketIdBytes = ticket.ticketId.toByteArray(Charsets.UTF_8)
            val pBytes = ticket.P.toByteArray()

            val out = mutableListOf<Byte>()
            out.add(ticketIdBytes.size.toByte())
            out.addAll(ticketIdBytes.toList())
            out.add(((pBytes.size ushr 8) and 0xFF).toByte())
            out.add((pBytes.size and 0xFF).toByte())
            out.addAll(pBytes.toList())

            return out.toByteArray() + SUCCESS
        }


        if (commandApdu.size >= 37 && commandApdu[1] == INS_CHALLENGE) {
            Log.d(TAG, "CHALLENGE request")
            val ticket = currentTicket ?: return FAILURE

            val challengeBytes = commandApdu.copyOfRange(5, 37)
            val challenge = BigInteger(1, challengeBytes).mod(CryptoHelper.q)

            Log.d(TAG, "Challenge: ${challenge.toString(16)}")

            val (S, R) = CryptoHelper.generateSchnorrSignature(ticket.acc, challenge)

            val sBytes = S.toByteArray()
            val rBytes = R.toByteArray()

            val response = ByteArray(4 + sBytes.size + rBytes.size)
            var offset = 0

            response[offset++] = ((sBytes.size shr 8) and 0xFF).toByte()
            response[offset++] = (sBytes.size and 0xFF).toByte()
            System.arraycopy(sBytes, 0, response, offset, sBytes.size)
            offset += sBytes.size

            response[offset++] = ((rBytes.size shr 8) and 0xFF).toByte()
            response[offset++] = (rBytes.size and 0xFF).toByte()
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
