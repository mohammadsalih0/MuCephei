# ✨ Mu Cephei

A serene, local-first nostalgic diary application for Android, crafted with 100% Jetpack Compose and Material 3. Inspired by Herschel's Garnet Star, **Mu Cephei** provides a calm and intimate notebook experience with rich media support, customizable glassmorphic themes, encrypted backups, and P2p mesh synchronization.

---

## 🌸 Key Features

### 📖 Serene Journaling & Feed Layouts
- **Handwritten Aesthetic**: Notebook interface with standard typography and customizable text scaling.
- **List & Grid View Modes**: Toggle seamlessly between multi-column grid layouts and detailed single-column list feeds.
- **Draft & Done Tracking**: Categorize notes into active drafts or completed entries with status filters.
- **Instant Search & Reaction Filtering**: Filter entries by keyword search or sticker reaction categories (❤️ Heart, ⭐ Star, ✨ Spark, 📌 Pin, ☕ Coffee).

### 🎨 Theme Studio & Glassmorphism Engine
- **Curated Theme Presets**:
  - **Linen Light**: Calm, warm paper aesthetic.
  - **Midnight**: Serene dark journal theme.
  - **AMOLED Black**: True black theme for OLED battery efficiency.
  - **Sunset Warmth**: Nostalgic golden hour tones.
  - **Lavender Dream**: Soft purple twilight hues.
  - **Forest Mist**: Deep botanical green tones.
  - **Ocean Breeze**: Cool coastal blue palette.
  - **Light Glass**: Frosted light glassmorphic surface.
  - **Dark Glass**: Deep dark frosted glass theme.
  - **Shining Stars**: Frosted dark glass with tiny animated stars that continuously shimmer between deep blue and white.
- **Glassify All Themes**: Global toggle applying frosted glass transparency across all UI surfaces and cards.
- **Custom Background Wallpapers**: Import custom wallpaper photos with adjustable blur intensity (0–25dp) and overlay opacity controls.
- **Automatic Vector Art Hiding**: Automatically hides background vector illustrations when a custom wallpaper is applied.
- **Dynamic Auto-Night Mode**: Automatically shifts to dark theme during nighttime hours (7 PM – 7 AM) or system dark theme.
- **Edge-to-Edge Experience**: Synchronizes system status and navigation bars dynamically with the active background.

### 🎙️ Multimodal Memory Attachments
- **Quick Voice Memo Recorder**: Dedicated voice note widget with real-time waveform visualizer and inline audio micro-player.
- **Camera Capture & Photo Attachments**: Quick camera launcher and downsampled thumbnail rendering powered by Coil.
- **Video & Lightbox Viewer**: Interactive video playback modal and full-screen image lightbox with pinch-to-zoom.
- **Document & File Integration**: Embedded PDF/file attachments with system open intent launcher.
- **Reaction Stickers**: Expressive sticker canvas overlay for placing handwritten reactions directly on note cards.

### 🔒 Privacy, Encrypted Backups & P2P Mesh Sync
- **AES-256-GCM Encrypted Backups**: Export and import encrypted `.mucephi` (`.mcb`) archives containing Room database records and media files with password-derived PBKDF2 keys.
- **Local P2P Mesh Sync**: Direct TCP socket co-op room management with 6-digit room code or QR pairing for offline notebook reconciliation via LWW-Element-Set CRDTs.
- **OpenVPN Tunnel Integration**: Import `.ovpn` configurations to establish private mesh tunnels for remote peer-to-peer synchronization.

---

## 🛠️ Architecture & Tech Stack

| Component | Technology |
| :--- | :--- |
| **Language** | Kotlin 2.x |
| **UI Framework** | Jetpack Compose, Material 3, Navigation 3 |
| **Database** | Room Database (SQLite with WAL mode & Coroutine Flows) |
| **Image Loading** | Coil 2.x (with dedicated memory & disk caching) |
| **Cryptography** | AES-256-GCM (`javax.crypto.Cipher`, PBKDF2 Key Derivation) |
| **Networking** | Java Sockets, Gson, OpenVPN config parser |
| **Min SDK** | 26 (Android 8.0 Oreo) |
| **Target SDK** | 34 (Android 14) |

---

## 🚀 Build & Setup Instructions

### Prerequisites
- **Android Studio**: Jellyfish / Ladybug or newer
- **JDK**: Java 17+
- **Android SDK**: API Level 34

### Building the Project

1. **Clone the repository**:
   ```bash
   git clone https://github.com/mucephei/mucephi.git
   cd mucephi
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install on connected device**:
   ```bash
   ./gradlew installDebug
   ```

4. **Run Unit Tests**:
   ```bash
   ./gradlew test
   ```

---

## 📜 License

Distributed under the **Apache License 2.0**. See `LICENSE` for details.
