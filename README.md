# PhoneAIOS - Autonomous Android AI Agent

PhoneAIOS is a minimalist, on-device AI agent designed to control your phone using natural language. It is powered by Google Gemma (2B) and operates 100% offline for maximum privacy.

## Features

- **Autonomous Phone Control**: Uses Accessibility Services to tap, swipe, and type on your behalf.
- **Screen Perception**: "Sees" the screen using a recursive node parser to adapt its plans in real-time.
- **Floating Agent Interface**: A persistent mic bubble and "Glow Edge" visualizer for background operation.
- **Safety First**: "Swipe to Approve" mechanism for sensitive actions like sending messages.
- **Voice Feedback**: Oral status updates using on-device Text-to-Speech.
- **Auto-Installer**: Automatically handles "Install" and "Update" prompts for any downloaded APK.

## Setup Instructions

1. **Download the APK**: Download the latest release from the [Releases](https://github.com/Devil1716/PhoneAIOS/releases) page.
2. **Install & Permissions**:
   - Enable **Accessibility Service** for PhoneAIOS.
   - Grant **Overlay Permission** (Appear on top).
   - Allow **Record Audio** for voice commands.
3. **Model Setup**:
   - Download the `gemma-2b-it.task` model file.
   - Place it in your phone's `/sdcard/Download/PhoneAIOS/model/` directory (or as directed in the app).
4. **Usage**:
   - Click the floating bubble.
   - Say: *"Download Subway Surfers"* or *"Send hello to Mom in WhatsApp"*.
   - Swipe to approve sensitive actions.

## Credits
Built with ❤️ for Autonomous Mobile AI.
