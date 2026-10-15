# MuCephei Backup Schema Documentation (Version: V2.0)

This document provides a comprehensive, exhaustive specification of all data types, fields, file structures, and encryption protocols backed up in **Version V2.0** of the MuCephei application backup/restore utility (`EncryptedBackupEngine`).

---

## 1. Summary of Changes in V2.0 (Relative to V1.1)

- **Archive Version Label**: Updated from `"V1.1"` to `"V2"` in `version.txt`.
- **New Data Types & Storage Backed Up**:
  - **Capsule / General App Settings**: Added Capsule & Haven swipe gesture sensitivity (`capsule_haven_sensitivity`) and Oasis toolbar swipe sensitivity (`oasis_sensitivity`) stored in `settings.json`.
  - **Haven AI Chat History & Context Window (`haven_ai.json`)**: Added full chat history (`chat_messages_json`), conversation context window (`context_length`), model path, temperature, token parameters, and prompt tweaks to maintain continuous multi-turn conversations and topic tracking.
  - **Oasis Browser State & Persistent Storage (`oasis_browser.json` & `oasis_data/`)**: Added open tabs (`open_tabs_json`), active tab ID, history (`history_json`), bookmarks (`bookmarks_json`), download directory, custom extensions, and WebView persistent storage (Local Storage, IndexedDB, WebSQL databases, and cookies from `app_webview/`) while **explicitly excluding browser cache** (`Cache`, `Code Cache`, `GPUCache`).
- **Backward Compatibility**: Fully compatible with legacy `V1`, unlabelled, and `V1.1` archives (gracefully initializing V2-specific structures to safe defaults when restoring older backups).

---

## 2. Archive File Structure (Unencrypted ZIP Layout)

When exported, the backup archive is formatted as a ZIP container with the following file and directory structure:

```
[Archive Root]
├── version.txt                # Version label string ("V2")
├── notes_db.json              # JSON array of all Room database note records (including NoteLocation)
├── settings.json              # JSON object of application theme, preferences, and swipe gesture sensitivities
├── haven_ai.json              # JSON object of Haven AI preferences, chat history, and context window
├── oasis_browser.json         # JSON object of Oasis browser preferences (tabs, history, bookmarks, downloads, extensions)
├── media/                     # Directory containing local media attachment blobs
│   ├── [filename.jpg]         # Photo attachments & video thumbnails
│   ├── [filename.mp4]         # Video attachments & voice memo audio
│   ├── [filename.pdf]         # Document attachments
│   └── [drawing_*.png]        # Handwritten canvas sketches
├── vpn_profiles/              # Directory containing OpenVPN configurations
│   └── [filename.ovpn]        # OpenVPN configuration profiles
├── wallpapers/                # Directory containing custom background wallpapers
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
- (Includes all fields from V1.1: `id`, `title`, `bodyText`, `createdAt`, `updatedAt`, `isFinished`, `isPrivate`, `isPinned`, `isFavorite`, `todoProgress`, `media`, `stickers`, `theme`, `location`, `version`, `deleted`).

---

### 3.3 App Preferences & Settings (`settings.json`)
- **Format**: JSON Object of `AppSettings`.
- **Source**: `ThemePreferences` and `app_swipe_settings`.

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
| `capsuleHavenSensitivity` | `Float` | **[New in V2]** Capsule & Haven swipe gesture sensitivity threshold |
| `oasisSensitivity` | `Float` | **[New in V2]** Oasis toolbar swipe sensitivity threshold |

---

### 3.4 Haven AI Preferences & Chat History (`haven_ai.json`) **[New in V2]**
- **Format**: JSON Object representing key-value map of `haven_ai_prefs`.
- **Key Fields**:
  - `chat_messages_json`: JSON string of `List<ChatMessage>` containing full conversation history and context window for maintaining ongoing topics.
  - `context_length`: `Float` representing context window token capacity.
  - `model_path`: `String?` active local GGUF model path.
  - `temperature`: `Float` creativity setting.
  - `max_tokens`: `Float` max answer length.
  - `guideline_prompt`: `String` prompt tweaks and guidelines.

---

### 3.5 Oasis Browser Preferences & Persistent Storage (`oasis_browser.json` & `oasis_data/`) **[New in V2]**
- **`oasis_browser.json`**: JSON Object representing `oasis_browser_prefs` (`open_tabs_json`, `active_tab_id`, `history_json`, `bookmarks_json`, `download_dir`, `custom_extensions`).
- **`oasis_data/`**: WebView persistent storage directory containing Local Storage, IndexedDB, WebSQL databases, and cookies (`app_webview/`), **strictly excluding** all browser cache folders (`Cache`, `Code Cache`, `GPUCache`).

---

### 3.6 Media Blobs (`media/`), VPN Profiles (`vpn_profiles/`), and Wallpapers (`wallpapers/`)
- (Same binary blob storage as V1.1).

---

## 4. Encryption Specification (AES-256-GCM)
- (Same AES-256-GCM encryption specification as V1.1).
