# Marmot (Two-Screen Handoff MVP)

Minimal Android app in **Kotlin + Jetpack Compose** to demo LAN-based "handoff" between two phones.

## Features
- Host / Join roles (same APK)
- TCP JSON-line protocol on port 9898
- System photo picker to choose an image (no storage permission)
- "Handoff" demo: drag image to right-edge to send HANDOFF event; peer shows a green bar entering from left

## How to run (Android Studio)
1. Open this folder as an existing project.
2. Let Gradle sync (ensure Android SDK 35).
3. Run on two devices (or one device + one emulator) connected to same Wi‑Fi.
4. One device: choose **Host** → **Start** (listens on :9898).
5. Another device: choose **Join**, input host IP (e.g., 192.168.1.5) → **Start**.
6. Pick an image, drag toward the right edge to trigger handoff.

> Currently only the **event** is sent, not the image data. Next steps:
> - NSD auto-discovery (no manual IP)
> - Thumbnail/original image transfer with chunking & CRC
> - Unified virtual canvas coordinates & smoother animations
