# CommuniDirect
**A secure, peer-to-peer, terminal-based messaging system.**

CommuniDirect is a lightweight P2P communication tool designed for users who live in the terminal. It uses modern elliptic-curve cryptography (Ed25519) for identity and a performance-optimized XOR-stream for message encryption.

## Features
* Nano-Style TUI: A familiar keyboard-driven interface built with Lanterna.
* Ed25519 Identities: Cryptographically secure identity verification for every message.
* External Editor Integration: Write your messages using your system's $EDITOR (Vim, Nano, etc.).
* Message Staging: Draft messages locally in ~/.communidirect/staged/ before sending.
* Multi-Platform Daemons: Background server support for macOS (launchd), Linux (systemd), and FreeBSD (rc.d).
* Symmetric Avatars: Visual identity verification using 5x5 ASCII avatars.

## Project Structure
The project is built with Java 24 and organized into a clean multi-module Gradle structure:
* :communidirect-common: Shared logic, configuration management, and the Ed25519 crypto engine.
* :communidirect-server: The background daemon that listens for, verifies, and archives messages.
* :communidirect-client: The interactive TUI for drafting, staging, and sending messages.

## Quick Start

### 1. Build from Source
```bash
./gradlew clean build aggregatedJavadoc
```

### 2. Install
Run the universal installer to set up directories, binary wrappers, and background services:
```bash
chmod +x install.sh
./install.sh
```

### 3. Generate Your Identity
On first run, the client will generate your Ed25519 key pair and initialize your keys.toml.
```bash
cd-client
```

## Client Keybindings
| Key | Action |
| :--- | :--- |
| ^N | New Message: Opens your $EDITOR to draft a new message. |
| ^S | Staged Messages: Browse and select drafts to be sent. |
| ^O | Send: (In Staged View) Dispatches the selected message. |
| ^V | View Received: Browse your archived conversation history. |
| ^X | Exit: Gracefully close the Lanterna TUI. |

## Configuration
All configuration and data are stored in ~/.communidirect/:
* identity.key: Your private Ed25519 key (Permissions: 600).
* keys/: Directory for trusted peer public keys (*.pub).
* msg/: Archive of all received and decrypted messages.
* config.toml: Port and network settings.

## Documentation
Project-wide Javadocs are automatically aggregated and available in the root /docs directory or can be generated via:
```bash
./gradlew aggregatedJavadoc
```

## Security Note
CommuniDirect uses Ed25519 for digital signatures to ensure the sender is who they claim to be. Messages are encrypted via an XOR stream with a unique session key sealed using the recipient's public key. Always ensure your identity.key is kept private.

## Contributing
If you are interested in improving the networking layer, adding new TUI windows, or optimizing the crypto engine, please see our [CONTRIBUTING.md](CONTRIBUTING.md) for local development guidelines and pull request standards.