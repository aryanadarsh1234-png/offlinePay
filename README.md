# UPI Offline Mesh — Deferred Payment Settlement over Bluetooth

A Spring Boot backend that solves a real problem: **how do you send money when you have zero internet?**

Built and extended by **Aryan Adarsh** as a deep-dive into distributed systems, cryptography, and backend engineering.

---

## The Problem

UPI requires internet. But millions of Indians live or work in areas with no connectivity — basements, rural zones, crowded stadiums, disaster zones. This project explores what a mesh-routed, cryptographically secure, offline payment system would look like.

---

## How It Works

Your phone encrypts a payment and broadcasts it over Bluetooth to nearby devices. Those devices relay it hop-by-hop — like a gossip protocol — until one phone walks into a 4G zone and uploads it to the backend. The backend then decrypts, verifies, and settles exactly once.

---

## Key Engineering Challenges I Solved

### 1. Privacy over untrusted devices
Random strangers carry your payment. They must not be able to read or modify it.

**Solution:** Hybrid encryption — AES-256-GCM encrypts the payload (with tamper detection via auth tag), and RSA-OAEP wraps the AES key. Only the server holds the private key.

### 2. The duplicate storm
5 phones may carry the same packet. All 5 upload simultaneously. Without protection, the sender gets debited 5 times.

**Solution:** `ConcurrentHashMap.putIfAbsent()` — atomic, JVM-local equivalent of Redis SETNX. The first thread through settles; the rest get `DUPLICATE_DROPPED` before any database work happens.

### 3. Replay attacks
An attacker captures a packet and replays it later.

**Solution:** Every packet contains a `signedAt` timestamp inside the encrypted payload. Server rejects anything older than 24 hours. Modifying the timestamp breaks the GCM auth tag.

### 4. Payment status confirmation (added by me)
After getting connectivity, the sender's phone needs to know: did my payment actually settle?

**Solution:** I built a new `GET /api/payment/status/{packetHash}` endpoint backed by `PaymentStatusService`. It checks the idempotency cache and database, returning `SETTLED`, `DUPLICATE_DROPPED`, or `PENDING`.

---

## Tech Stack

- Java 17+ / Spring Boot 3.3
- Spring Data JPA + H2 (in-memory)
- Java Cryptography Architecture (RSA-OAEP + AES-256-GCM)
- JUnit 5 + CountDownLatch for concurrency testing
- Maven

---

## Project Structure
rc/main/java/com/demo/upimesh/
├── crypto/
│   ├── HybridCryptoService.java     # RSA-OAEP + AES-256-GCM encrypt/decrypt
│   └── ServerKeyHolder.java         # Generates RSA keypair on startup
├── service/
│   ├── BridgeIngestionService.java  # THE pipeline: hash→claim→decrypt→settle
│   ├── IdempotencyService.java      # ConcurrentHashMap-based dedup cache
│   ├── SettlementService.java       # @Transactional debit + credit + ledger
│   ├── MeshSimulatorService.java    # Software Bluetooth gossip simulator
│   └── PaymentStatusService.java   # NEW — payment status lookup by hash
├── controller/
│   └── ApiController.java           # REST endpoints including new status API
└── model/
├── Transaction.java
├── Account.java
└── TransactionRepository.java

---

## Running Locally

**Prerequisites:** Java 17+

```bash
git clone https://github.com/aryanadarsh1234-png/upi-offline-mesh.git
cd upi-offline-mesh
./mvnw spring-boot:run      # Linux/Mac
.\mvnw.cmd spring-boot:run  # Windows
```

Open **http://localhost:8080** for the live demo dashboard.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/demo/send` | Encrypt and inject a payment into the mesh |
| POST | `/api/mesh/gossip` | Run one gossip round across devices |
| POST | `/api/mesh/flush` | Bridge nodes upload all packets (parallel) |
| GET | `/api/payment/status/{hash}` | **NEW** — Check settlement status by packet hash |
| GET | `/api/accounts` | List all account balances |
| GET | `/api/transactions` | List recent settled transactions |
| GET | `/api/server-key` | Fetch server's RSA public key |

---

## What I Added

The original project had no way for a sender to confirm their payment settled.
I designed and implemented the full status-check feature:

- Added `findByPacketHash()` query to `TransactionRepository`
- Added `isDuplicate()` method to `IdempotencyService`
- Created `PaymentStatusService` with three-state logic
- Exposed `GET /api/payment/status/{packetHash}` in `ApiController`

**Example response:**
```json
{
  "status": "SETTLED",
  "from": "alice@demo",
  "to": "bob@demo",
  "amount": 500.0,
  "settledAt": "2026-06-11T13:28:57.973443Z"
}
```

---

## Known Limitations (honest engineering)

- **Double spending:** Two payments from the same account in different mesh zones — whichever reaches the backend first wins; the second is rejected
- **In-memory only:** Idempotency cache resets on server restart (production would use Redis)
- **Software simulation:** Real deployment needs Android BLE/Wi-Fi Direct

---

## What I Learned

- How hybrid encryption (RSA + AES-GCM) works in practice using Java's JCA
- Why `ConcurrentHashMap.putIfAbsent` is the right tool for atomic deduplication
- How `@Transactional` and optimistic locking prevent race conditions in settlement
- Designing REST APIs and Spring Boot service layers from scratch