# Contributing to CommuniDirect

Thank you for your interest in contributing to CommuniDirect. This document outlines the development standards and the process for submitting improvements to the project.

## Development Environment
The project is built using Java 24 and Gradle. To contribute, ensure your environment meets the following requirements:
* JDK 24 or higher.
* Gradle 8.0 or higher.
* A terminal supporting Xterm-256color for TUI testing.

## Build and Test
Before submitting a pull request, ensure the project builds correctly and all documentation is generated:

```bash
./gradlew clean build aggregatedJavadoc
```

## Project Architecture
CommuniDirect is a multi-module project. Please place your changes in the appropriate module:
* **communidirect-common**: Core logic, Ed25519 cryptography implementation, and configuration management.
* **communidirect-client**: Terminal User Interface (TUI) logic using Lanterna.
* **communidirect-server**: Network listener, message verification, and background daemon logic.

## Technical Guidelines

### Cryptography (Ed25519)
Any changes to the cryptographic layer must be thoroughly documented. CommuniDirect relies on Ed25519 for identity verification. Do not introduce dependencies on external crypto libraries without discussing it in an issue first; the goal is to keep the binary footprint small.

### TUI Development (Lanterna)
When working on the client module:
* **Thread Safety**: Lanterna's Screen object is not thread-safe. Ensure all UI updates are synchronized or handled on the main TUI thread.
* **Compatibility**: Test UI changes on multiple terminal emulators (e.g., iTerm2, Windows Terminal, Linux Console) to ensure ASCII layouts remain functional.

### Coding Standards
* Follow standard Java naming conventions.
* Provide Javadoc for all new public methods and classes.
* Ensure all files end with a single newline.
* Use the Version Catalog (gradle/libs.versions.toml) to manage dependencies.

## Pull Request Process
1. Fork the repository and create your branch from `main`.
2. Commit your changes with clear, descriptive messages.
3. Update the `aggregatedJavadoc` and verify the output in the root `/docs` directory.
4. Submit a Pull Request with a summary of the changes and the reasoning behind them.

## Documentation
Documentation is generated at the project root using the aggregated Javadoc task. If you add new modules or significant functionality, ensure the `aggregatedJavadoc` task in the root `build.gradle` is updated to include the new source sets.