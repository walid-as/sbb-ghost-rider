package com.example.ghostrider.model

import java.math.BigInteger

data class Ticket(
    val ticketId: String,
    val nonce: ByteArray,
    val acc: BigInteger,
    val P: BigInteger,
    val timestamp: Long = System.currentTimeMillis(),
    val addedToWallet: Boolean = false,
    val nfcEnabled: Boolean = false,
    val publicKeys: List<BigInteger> = listOf(P)  // All registered keys
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Ticket
        if (ticketId != other.ticketId) return false
        return true
    }

    override fun hashCode(): Int {
        return ticketId.hashCode()
    }
}

data class VerificationResult(
    val ticketId: String,
    val isValid: Boolean,
    val challenge: String,
    val timestamp: Long,
    val location: String,
    val matchedKeyIndex: Int = -1
)
