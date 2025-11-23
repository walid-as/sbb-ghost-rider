# GhostRider - Anonymous Ticket System

A complete Android demonstration of an anonymous ticket issuance and verification system using Schnorr-style signatures and NFC communication.

## Features

- **CLIENT MODE**: Generate anonymous tickets with device-bound secrets
- **INSPECTOR MODE**: Verify tickets via NFC using zero-knowledge proofs
- **Cryptographic Protocol**: Schnorr-like signature scheme with challenge-response
- **NFC Communication**: HCE (client) and Reader Mode (inspector)
- **Secure Storage**: Android KeyStore + EncryptedSharedPreferences

## Architecture

### Cryptographic Flow

1. **Ticket Generation (Client)**:
   - Generate random nonce (32 bytes)
   - Compute `ticketId = SHA-256(nonce)`
   - Derive secret `acc` from device-bound ECDSA key signature
   - Compute public key `P = g^acc mod p`
   - Store ticket securely

2. **Verification (Inspector)**:
   - Inspector generates challenge `c = SHA-256(timestamp || lat || lon)`
   - Sends challenge via NFC to client
   - Client responds with Schnorr signature `(S, R)`:
      - `R = g^n mod p` (n is random)
      - `S = (n + acc * c) mod q`
   - Inspector verifies: `g^S ≡ R * P^c`

### Schnorr Parameters

- **p**: RFC 3526 2048-bit MODP Group 14 prime (safe prime)
- **g**: 2

### NFC Protocol

**Client (HCE Service)**:
- AID: `F0010203040506`
- Commands:
   - `INS=0xA1`: Returns ticketId
   - `INS=0xA2`: Receives challenge, returns `(S, R)`

**Inspector (Reader Mode)**:
- Sends SELECT AID
- Requests ticketId
- Sends challenge
- Receives and verifies signature

## Build & Run

### Prerequisites
- Android Studio Hedgehog or later
- Two physical Android devices with NFC (API 26+)
- NFC enabled on both devices

### Setup

1. **Clone/Create Project**

2. **Build**

3. **Install on Both Devices**


## Testing Two-Device NFC Flow

### Device 1 (Client):

1. Open app → CLIENT MODE
2. Tap "Generate New Ticket"
3. Tap "Add to Wallet" on a ticket
4. **Leave app open** (HCE service must be active)

### Device 2 (Inspector):

1. Open app → INSPECTOR MODE
2. Tap "Start NFC Reader"
3. Hold Device 2 back-to-back with Device 1
4. Wait 2-5 seconds for NFC exchange
5. View verification result (VALID/INVALID)

### Local Demo (Single Device):

1. CLIENT MODE → "Run Local Demo (No NFC)"
2. This simulates the full protocol locally to prove correctness

## Key Files

- **MainActivity.kt**: Navigation and app entry
- **ClientScreen.kt**: Ticket generation and wallet UI
- **InspectorScreen.kt**: NFC reader and verification UI
- **HceService.kt**: NFC HCE APDU handler (client side)
- **NfcReader.kt**: NFC reader mode implementation (inspector side)
- **CryptoHelper.kt**: Schnorr parameters, key derivation, signature generation/verification
- **Storage.kt**: Secure ticket storage (EncryptedSharedPreferences)
- **Models.kt**: Data classes

## Simulated Wallet

**Current Implementation**: When "Add to Wallet" is tapped, the ticket is marked in local storage and becomes the active ticket for NFC presentation.

## License

MIT License - Demo purposes only
