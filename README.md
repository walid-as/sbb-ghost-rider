# AnonTicketDemo - Anonymous Ticket System

A complete Android demonstration of an anonymous ticket issuance and verification system using Schnorr-style signatures and NFC communication.

**Whiteboard used:** `/mnt/data/6133027e-2666-42e9-8df3-2d04c326a8d0.png`

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
    - Inspector generates challenge `c = SHA-256(timestamp || lat || lon) mod q`
    - Sends challenge via NFC to client
    - Client responds with Schnorr signature `(S, R)`:
        - `R = g^n mod p` (n is random)
        - `S = (n + acc * c) mod q`
    - Inspector verifies: `g^S ≡ R * P^c (mod p)`

### Schnorr Parameters

- **p**: RFC 3526 2048-bit MODP Group 14 prime (safe prime)
- **g**: 2
- **q**: 256-bit prime (exponent group order)

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

1. **Clone/Create Project**:
