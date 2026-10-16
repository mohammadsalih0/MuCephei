# MuCephei Backup Schema Documentation (Version: V2.0)

This document provides a comprehensive, exhaustive specification of all data types, fields, file structures, and encryption protocols backed up in **Version V2.0** of the MuCephei application backup/restore utility (`EncryptedBackupEngine`).

---

## 1. Summary of Changes in V2.0 (Relative to V1.1)

- **Archive Version Label**: Updated from `"V1.1"` to `"V2"` in `version.txt`.
- **New Data Types & Storage Backed Up**:
  - **Capsule / General App Settings (`settings.json`)**: Added Capsule & Haven swipe gesture sensitivity (`capsule_haven_sensitivity`), Oasis toolbar swipe sensitivity (`oasis_sensitivity`), swipe navigation mode toggle (`swipe_mode_full`), edge gesture width (`edge_width_dp`), and recent room code (`lastRoomCode`).
  - **Haven AI Chat History & Context Window (`haven_ai.json`)**: Added full chat message history (`chat_messages_json`), conversation context window (`context_length`), model path, temperature, token parameters, top-P/top-K/repeat penalty, system guidelines (`guideline_prompt`), prompt tweak parameters, and saved local model info entries.
  - **Oasis Browser State & Persistent Storage (`oasis_browser.json` & `oasis_data/`)**: Added open tabs (`open_tabs_json`), active tab ID (`active_tab_id`), history (`history_json`), bookmarks (`bookmarks_json`), speed dials (`speed_dial_json`), download directory (`download_dir`), custom extension/content-type preferences (`custom_extensions`), toolbar orientation/size/transparency/auto-popup settings, and WebView persistent storage (Local Storage, IndexedDB, WebSQL databases, and cookies from `app_webview/`) while **explicitly excluding browser cache** (`Cache`, `Code Cache`, `GPUCache`).
  - **Sketches & Handwritten Canvas Drawings (`drawings/`)**: Added backup and restore support for handwritten canvas sketches stored in `context.filesDir/mucephei_drawings`.
  - **Voice Memos & Audio Recordings (`audio/`)**: Added backup and restore support for voice recordings stored in `context.filesDir/mucephi_audio`.
  - **P2P Connection Profiles (`connections/`)**: Added backup and restore support for saved connection profiles (`.muconn` JSON files) stored in `context.filesDir/mucephi_connections`.
- **Dynamic Path Auto-Healing**: Restored notes automatically sanitize local file paths for attached photos, videos, sketches, voice memos, and wallpapers to guarantee media links resolve on any device or storage path.
- **Backward Compatibility**: Fully compatible with legacy `V1`, unlabelled, and `V1.1` archives (gracefully initializing V2-specific structures to safe defaults when restoring older backups).

---

## 2. Archive File Structure (Unencrypted ZIP Layout)

When exported, the backup archive is formatted as a ZIP container with the following file and directory structure:

```
[Archive Root]
├── version.txt                # Version label string ("V2")
├── notes_db.json              # JSON array of all Room database note records (including NoteLocation)
├── settings.json              # JSON object of application theme, preferences, room codes, and swipe gesture sensitivities
├── haven_ai.json              # JSON object of Haven AI preferences, chat history, models info, and context window
├── oasis_browser.json         # JSON object of Oasis browser preferences (tabs, history, bookmarks, speed dials, downloads, toolbar, extensions)
├── media/                     # Directory containing local media attachment blobs from mucephei_media
│   ├── [filename.jpg]         # Photo attachments & video thumbnails
│   ├── [filename.mp4]         # Video attachments
│   └── [filename.pdf]         # Document attachments
├── drawings/                  # Directory containing handwritten canvas sketches from mucephei_drawings
│   └── [drawing_*.png]        # Canvas drawings
├── audio/                     # Directory containing voice memo audio recordings from mucephi_audio
│   └── [rec_*.m4a]            # Voice recordings
├── connections/               # Directory containing saved P2P connection profiles (.muconn) from mucephi_connections
│   └── [name_code.muconn]     # Saved connection profiles
├── vpn_profiles/              # Directory containing OpenVPN configurations (.ovpn) from mucephi_vpn
│   └── [filename.ovpn]        # OpenVPN configuration profiles
├── wallpapers/                # Directory containing custom background wallpapers from wallpapers
│   └── [filename.jpg/png]     # Custom wallpaper image files
└── oasis_data/                # Directory containing Oasis WebView persistent storage (local storage, cookies, databases) excluding cache
    └── [app_webview/...]      # Persistent web data files (excluding Cache, Code Cache, GPUCache)
```

---

## 3. Documented Data Types & Schemas

### 3.1 Archive Version File (`version.txt`)
- **Format**: Plain text (UTF-8)
- **Content**: `"V2"`

---

### 3.2 Notes Database (`notes_db.json`)
- **Format**: JSON Array of `NoteEntity` objects.
- **Source**: Room Database (`notes` table).

#### `NoteEntity` Schema
| Field Name | Type | Description |
| :--- | :--- | :--- |
| `id` | `String` | Unique UUID identifier for the note |
| `title` | `String` | Note title string |
| `bodyText` | `String` | Main body text content of the journal entry |
| `createdAt` | `Long` | Epoch timestamp (ms) when the note was created |
| `updatedAt` | `Long` | Epoch timestamp (ms) when the note was last updated |
| `isFinished` | `Boolean` | Completion status (`false` = Draft, `true` = Ready) |
| `isPrivate` | `Boolean` | Note privacy toggle (`true` = Local only, `false` = P2P Shared) |
| `isPinned` | `Boolean` | Pinned status flag |
| `isFavorite` | `Boolean` | Favorited status flag |
| `todoProgress` | `TodoProgress` | Nested object tracking checklist item completion |
| `media` | `List<MediaItem>` | Array of attached media items |
| `stickers` | `List<ReactionSticker>` | Array of placed reaction stickers and emojis |
| `theme` | `NoteTheme?` | Per-note theme customization object (or `null`) |
| `location` | `NoteLocation?` | Geotag location metadata object (or `null`) |
| `version` | `Long` | CRDT LWW version counter for conflict resolution |
| `deleted` | `Boolean` | Soft-delete tombstone status flag |

---

### 3.3 App Preferences & Settings (`settings.json`)
- **Format**: JSON Object of `AppSettings`.
- **Source**: `ThemePreferences` (`mu_cephei_theme_prefs`) and `app_swipe_settings`.

#### `AppSettings` Schema (V2 Additions Highlighted)
| Field Name | Type | Description |
| :--- | :--- | :--- |
| `selectedPreset` | `String` | Active global `ThemePreset` enum name |
| `customThemeJson` | `String?` | Serialized JSON string of custom `NoteTheme` |
| `wallpaperPath` | `String?` | File path to active global wallpaper image |
| `blurRadius` | `Float` | Global wallpaper blur radius (dp) |
| `wallpaperAlpha` | `Float` | Global wallpaper opacity alpha (0.0 to 1.0) |
| `autoNightModeEnabled` | `Boolean` | Auto dark theme toggle setting |
| `fontScale` | `Float` | Global UI typography scale factor |
| `isGridView` | `Boolean` | Feed view layout mode (`true` = Grid, `false` = List) |
| `vectorOpacity` | `Float` | Opacity for vector background patterns |
| `hideVectors` | `Boolean` | Toggle to hide vector background patterns |
| `textTransparencyEnabled` | `Boolean` | Text container transparency toggle setting |
| `textTransparencyAlpha` | `Float` | Text container transparency alpha value |
| `glassifyEnabled` | `Boolean` | Glassmorphism visual effects toggle setting |
| `lastHostIp` | `String` | Last connected P2P mesh host IP address |
| `customRoomCode` | `String` | Saved custom P2P co-op room code setting |
| `lastRoomCode` | `String?` | **[New in V2]** Last joined P2P room code setting |
| `swipeModeFull` | `Boolean?` | **[New in V2]** Full-screen vs edge-only swipe gesture toggle |
| `edgeWidthDp` | `Float?` | **[New in V2]** Touch edge swipe detection width in DP |
| `capsuleHavenSensitivity` | `Float?` | **[New in V2]** Capsule & Haven swipe gesture sensitivity threshold |
| `oasisSensitivity` | `Float?` | **[New in V2]** Oasis toolbar swipe sensitivity threshold |

---

### 3.4 Haven AI Preferences & Chat History (`haven_ai.json`) **[New in V2]**
- **Format**: JSON Object representing key-value map of `haven_ai_prefs`.
- **Key Fields**:
  - `chat_messages_json`: JSON string of `List<ChatMessage>` containing full conversation history and context window for maintaining ongoing topics.
  - `context_length`: Context window token capacity (e.g. 4096).
  - `model_path`: Active local GGUF model path.
  - `temperature`: Creativity temperature parameter.
  - `max_tokens`: Max answer length parameter.
  - `top_p` & `top_k`: Sampling parameters.
  - `repeat_penalty`: Repetition penalty parameter.
  - `cpu_cores`: Number of CPU threads allocated for inference.
  - `ai_preset`: Selected AI prompt preset mode.
  - `guideline_prompt`: Custom system prompt guidelines.
  - `tweak_*`: Prompt tweak options (word count, tone, format, audience, focus).
  - Saved model infos.

---

### 3.5 Oasis Browser Preferences & Persistent Storage (`oasis_browser.json` & `oasis_data/`) **[New in V2]**
- **`oasis_browser.json`**: JSON Object representing `oasis_browser_prefs` (`open_tabs_json`, `active_tab_id`, `history_json`, `bookmarks_json`, `speed_dial_json`, `download_dir`, `custom_extensions`, `auto_show_downloads`, `toolbar_disabled_forever`, `toolbar_orientation`, `toolbar_transparency`, `toolbar_size`, `toolbar_auto_popup`, `toolbar_suppress_on_page_load`, `toolbar_disabled_functions`, `toolbar_function_order`).
- **`oasis_data/`**: WebView persistent storage directory containing Local Storage, IndexedDB, WebSQL databases, and cookies (`app_webview/`), **strictly excluding** all browser cache folders (`Cache`, `Code Cache`, `GPUCache`).

---

### 3.6 Media Blobs, Sketches, Audio, Connections, VPN Profiles, and Wallpapers
- `media/`: Raw binary files from `context.filesDir/mucephei_media`.
- `drawings/`: Handwritten canvas sketch image files from `context.filesDir/mucephei_drawings`.
- `audio/`: Voice memo recording audio files from `context.filesDir/mucephi_audio`.
- `connections/`: Saved P2P connection profile `.muconn` files from `context.filesDir/mucephi_connections`.
- `vpn_profiles/`: OpenVPN configuration `.ovpn` files from `context.filesDir/mucephi_vpn`.
- `wallpapers/`: Image files from `context.filesDir/wallpapers`.

---

## 4. Encryption Specification (AES-256-GCM)
- **Algorithm**: `AES-256-GCM` (`AES/GCM/NoPadding`)
- **Key Derivation**: `PBKDF2WithHmacSHA256`
  - Iterations: `10,000`
  - Salt Length: `16 bytes`
  - Key Length: `256 bits`
- **Initialization Vector (IV)**: `12 bytes`
- **Authentication Tag**: `128 bits`
- **Binary Encrypted File Layout**:
  ```
  [0..15 bytes]  : Salt (16 bytes)
  [16..27 bytes] : IV (12 bytes)
  [28..N bytes]  : AES-256-GCM Encrypted Zip Ciphertext (with 16-byte auth tag)
  ```
