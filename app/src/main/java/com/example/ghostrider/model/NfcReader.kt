package com.example.ghostrider.model

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import java.math.BigInteger

/**
 * NFC Reader for Inspector mode.
 * Uses enableReaderMode to detect tags and exchange APDUs via IsoDep.
 */
class NfcReader(
    private val activity: Activity,
    private val onResult: (VerificationResult) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        private const val TAG = "NfcReader"
        private val AID = byteArrayOf(
            0xF0.toByte(), 0x01, 0x02, 0x03, 0x04, 0x05, 0x06
        )
    }

    private val nfcAdapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private var isEnabled = false

    fun enable() {
        if (nfcAdapter == null) {
            onError("NFC not available on this device")
            return
        }

        if (!nfcAdapter.isEnabled) {
            onError("NFC is disabled. Please enable it in settings.")
            return
        }

        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        nfcAdapter.enableReaderMode(activity, { tag ->
            handleTag(tag)
        }, flags, Bundle())

        isEnabled = true
        Log.d(TAG, "NFC Reader enabled")
    }

    fun disable() {
        nfcAdapter?.disableReaderMode(activity)
        isEnabled = false
        Log.d(TAG, "NFC Reader disabled")
    }

    private fun handleTag(tag: Tag) {
        Log.d(TAG, "Tag detected: ${tag.id.joinToString(" ") { "%02X".format(it) }}")

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            onError("Tag does not support ISO-DEP")
            return
        }

        try {
            isoDep.connect()
            isoDep.timeout = 5000

            // Step 1: SELECT AID
            val selectApdu = buildSelectApdu()
            val selectResponse = isoDep.transceive(selectApdu)
            if (!isSuccess(selectResponse)) {
                onError("SELECT failed: ${selectResponse.joinToString(" ") { "%02X".format(it) }}")
                isoDep.close()
                return
            }
            Log.d(TAG, "SELECT successful")

            // Step 2: GET TICKET ID (INS=0xA1)
            val getTicketIdApdu = byteArrayOf(0x00, 0xA1.toByte(), 0x00, 0x00, 0x00)
            val ticketIdResponse = isoDep.transceive(getTicketIdApdu)
            if (!isSuccess(ticketIdResponse)) {
                onError("GET_TICKET_ID failed")
                isoDep.close()
                return
            }
            val ticketId = String(ticketIdResponse.copyOfRange(0, ticketIdResponse.size - 2))
            Log.d(TAG, "Ticket ID: $ticketId")

            // Retrieve P from stored tickets (inspector must have P; in real scenario it's sent via NFC or pre-registered)
            // For this demo, we'll assume inspector can query or we send P. Let's add P to the protocol.
            // Simplified: Inspector retrieves P from a local registry or the ticket itself sends it.
            // For demo purposes, let's assume inspector has access to Storage (both devices share same app).
            // In production, P would be transmitted or looked up from a central registry.

            val ticket = Storage.getTicket(ticketId)
            if (ticket == null) {
                onError("Ticket not found in local storage. In production, P would be transmitted via NFC.")
                isoDep.close()
                return
            }
            val P = ticket.P

            // Step 3: Generate challenge
            val timestamp = System.currentTimeMillis()
            val latitude = 0.0 // Simplified; in production get from GPS
            val longitude = 0.0
            val challenge = CryptoHelper.computeChallenge(timestamp, latitude, longitude)
            Log.d(TAG, "Challenge: ${challenge.toString(16)}")

            // Step 4: Send CHALLENGE (INS=0xA2)
            val challengeBytes = challenge.toByteArray()
            val challengePadded = ByteArray(32)
            val offset = maxOf(0, 32 - challengeBytes.size)
            System.arraycopy(challengeBytes, maxOf(0, challengeBytes.size - 32),
                challengePadded, offset, minOf(32, challengeBytes.size))

            val challengeApdu = byteArrayOf(
                0x00, 0xA2.toByte(), 0x00, 0x00, 32
            ) + challengePadded + byteArrayOf(0x00)

            val signatureResponse = isoDep.transceive(challengeApdu)
            if (!isSuccess(signatureResponse)) {
                onError("CHALLENGE failed")
                isoDep.close()
                return
            }

            // Parse (S, R) from response
            val data = signatureResponse.copyOfRange(0, signatureResponse.size - 2)
            var idx = 0
            val sLen = ((data[idx++].toInt() and 0xFF) shl 8) or (data[idx++].toInt() and 0xFF)
            val sBytes = data.copyOfRange(idx, idx + sLen)
            idx += sLen
            val rLen = ((data[idx++].toInt() and 0xFF) shl 8) or (data[idx++].toInt() and 0xFF)
            val rBytes = data.copyOfRange(idx, idx + rLen)

            val S = BigInteger(sBytes)
            val R = BigInteger(rBytes)

            Log.d(TAG, "S: ${S.toString(16)}")
            Log.d(TAG, "R: ${R.toString(16)}")

            // Step 5: Verify signature
            val isValid = CryptoHelper.verifySchnorrSignature(P, challenge, S, R)
            Log.d(TAG, "Verification result: $isValid")

            val result = VerificationResult(
                ticketId = ticketId,
                isValid = isValid,
                challenge = challenge.toString(16).take(16) + "...",
                timestamp = timestamp,
                location = "0.0, 0.0 (demo)"
            )

            onResult(result)
            isoDep.close()

        } catch (e: Exception) {
            Log.e(TAG, "Error during NFC communication", e)
            onError("NFC error: ${e.message}")
            try {
                isoDep.close()
            } catch (ignored: Exception) {}
        }
    }

    private fun buildSelectApdu(): ByteArray {
        return byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()
        ) + AID + byteArrayOf(0x00)
    }

    private fun isSuccess(response: ByteArray): Boolean {
        return response.size >= 2 &&
                response[response.size - 2] == 0x90.toByte() &&
                response[response.size - 1] == 0x00.toByte()
    }
}
