package com.example.ghostrider.model

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.math.BigInteger
import java.security.*
import java.security.spec.ECGenParameterSpec

object CryptoHelper {

  private const val TAG = "CryptoHelper"

  // RFC 3526 2048-bit MODP Group 14 prime (safe prime)
  val P_HEX =
      """
      FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1
      29024E088A67CC74020BBEA63B139B22514A08798E3404DD
      EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245
      E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED
      EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3D
      C2007CB8A163BF0598DA48361C55D39A69163FA8FD24CF5F
      83655D23DCA3AD961C62F356208552BB9ED529077096966D
      670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B
      E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9
      DE2BCBF6955817183995497CEA956AE515D2261898FA0510
      15728E5A8AACAA68FFFFFFFFFFFFFFFF
      """
          .trimIndent()
          .replace("\n", "")
          .replace(" ", "")

  val p: BigInteger = BigInteger(P_HEX, 16)
  val g: BigInteger = BigInteger.valueOf(2)

  // q should be (p-1)/2 for a safe prime, but that's huge.
  // For Schnorr we need a smaller subgroup order.
  // Using a fixed 256-bit prime for the exponent group order
  val q: BigInteger =
      BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)

  private const val KEYSTORE_ALIAS = "atd_device_key"

  /** Initialize or retrieve the device-bound EC key from Android KeyStore. */
  fun getOrCreateDeviceKey(): KeyPair {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
      try {
        val privateKey = keyStore.getKey(KEYSTORE_ALIAS, null) as PrivateKey
        val publicKey = keyStore.getCertificate(KEYSTORE_ALIAS).publicKey
        Log.d(TAG, "Loaded existing device key")
        return KeyPair(publicKey, privateKey)
      } catch (e: Exception) {
        Log.e(TAG, "Error loading key, recreating", e)
        keyStore.deleteEntry(KEYSTORE_ALIAS)
      }
    }

    // Create new EC key pair
    val keyPairGenerator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")

    val parameterSpec =
        KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
            )
            .apply {
              setDigests(KeyProperties.DIGEST_SHA256)
              setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
              setUserAuthenticationRequired(false)
            }
            .build()

    keyPairGenerator.initialize(parameterSpec)
    val keyPair = keyPairGenerator.generateKeyPair()
    Log.d(TAG, "Created new device key")
    return keyPair
  }

  /**
   * Derive 'acc' secret for a ticket by signing ticketId with device key. acc =
   * SHA-256(ECDSA_signature(ticketId)) mod q
   */
  fun deriveTicketSecret(ticketId: String): BigInteger {
    val keyPair = getOrCreateDeviceKey()
    val signature = Signature.getInstance("SHA256withECDSA")
    signature.initSign(keyPair.private)
    signature.update(ticketId.toByteArray())
    val signatureBytes = signature.sign()

    // Hash the signature to get acc
    val digest = MessageDigest.getInstance("SHA-256")
    val accBytes = digest.digest(signatureBytes)
    val accBig = BigInteger(1, accBytes)

    Log.d(TAG, "Derived acc for ticket $ticketId: ${accBig.toString(16).take(16)}...")
    return accBig
  }

  /** Compute P = g^acc mod p (public key for ticket) */
  fun computePublicKey(acc: BigInteger): BigInteger {
    val P = g.modPow(acc, p)
    Log.d(TAG, "Computed P: ${P.toString(16).take(16)}...")
    return P
  }

  /** Generate a random nonce (32 bytes) */
  fun generateNonce(): ByteArray {
    val nonce = ByteArray(32)
    SecureRandom().nextBytes(nonce)
    return nonce
  }

  /** Compute SHA-256 hash and return hex string */
  fun sha256Hex(input: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input)
    return hash.joinToString("") { "%02x".format(it) }
  }

  /** Compute ticketId = SHA-256(nonce) as hex string */
  fun computeTicketId(nonce: ByteArray): String {
    return sha256Hex(nonce)
  }

  /**
   * Client-side: Generate Schnorr signature (S, R) for challenge c. R = g^n mod p S = (n + acc * c)
   * mod q
   */
  fun generateSchnorrSignature(
      acc: BigInteger,
      challenge: BigInteger,
  ): Pair<BigInteger, BigInteger> {
    // Generate random n in [1, q-1] (avoid 0)
    var n: BigInteger
    do {
      n = BigInteger(q.bitLength(), SecureRandom())
    } while (n == BigInteger.ZERO)

    // R = g^n mod p
    val R = g.modPow(n, p)

    // S = (n + acc * c) mod q
    val accTimesC = acc.multiply(challenge)
    val S = n.add(accTimesC)

    Log.d(TAG, "Generated signature:")
    Log.d(TAG, "  n: ${n.toString(16).take(16)}...")
    Log.d(TAG, "  R: ${R.toString(16).take(16)}...")
    Log.d(TAG, "  S: ${S.toString(16).take(16)}...")
    Log.d(TAG, "  acc: ${acc.toString(16).take(16)}...")
    Log.d(TAG, "  challenge: ${challenge.toString(16).take(16)}...")

    return Pair(S, R)
  }

  /** Inspector-side: Verify Schnorr signature. Check: g^S mod p == (R * P^c) mod p */
  fun verifySchnorrSignature(
      P: BigInteger,
      challenge: BigInteger,
      S: BigInteger,
      R: BigInteger,
  ): Boolean {
    try {
      // left = g^S mod p
      val left = g.modPow(S, p)

      // right = (R * P^c) mod p
      val Pc = P.modPow(challenge, p)
      val right = R.multiply(Pc).mod(p)

      val isValid = left == right

      Log.d(TAG, "Verification:")
      Log.d(TAG, "  P: ${P.toString(16).take(16)}...")
      Log.d(TAG, "  challenge: ${challenge.toString(16).take(16)}...")
      Log.d(TAG, "  S: ${S.toString(16).take(16)}...")
      Log.d(TAG, "  R: ${R.toString(16).take(16)}...")
      Log.d(TAG, "  left (g^S): ${left.toString(16).take(16)}...")
      Log.d(TAG, "  right (R*P^c): ${right.toString(16).take(16)}...")
      Log.d(TAG, "  MATCH: $isValid")

      return isValid
    } catch (e: Exception) {
      Log.e(TAG, "Verification error", e)
      return false
    }
  }

  /** Compute challenge from timestamp and location. c = SHA-256(timestamp || lat || lon) mod q */
  fun computeChallenge(timestamp: Long, latitude: Double, longitude: Double): BigInteger {
    val data = "$timestamp|$latitude|$longitude"
    val hash = sha256Hex(data.toByteArray())
    val challengeBig = BigInteger(hash, 16)
    Log.d(TAG, "Challenge computed: ${challengeBig.toString(16).take(16)}...")
    return challengeBig
  }
}
