# MuCephei Backup Schema Documentation (Version: V1)

This document provides a comprehensive, exhaustive specification of all data types, fields, file structures, and encryption protocols backed up in **Version V1** of the MuCephei application backup/restore utility (`EncryptedBackupEngine`).

---

## 1. Archive File Structure (Unencrypted ZIP Layout)

When exported, the backup archive is formatted as a ZIP container with the following file and directory structure:

```
[Archive Root]
├── version.txt            # Version label string ("V1")
├── notes_db.json          # JSON array of all Room database note records
├── settings.json          # JSON object of application theme and user preferences
├── media/                 # Directory containing local media attachment blobs
│   ├── [filename.jpg]     # Photo attachments
│   ├── [filename.mp4]     # Video attachments & voice memo audio
│   ├── [filename.pdf]     # Document attachments
│   └── [drawing_*.png]    # Handwritten canvas sketches
├── vpn_profiles/          # Directory containing OpenVPN configurations
│   └── [filename.ovpn]    # OpenVPN configuration profiles
└── wallpapers/            # Directory containing custom background wallpapers
    └── [filename.jpg/png] # Custom wallpaper image files
```

---

## 2. Documented Data Types & Schemas

### 2.1 Archive Version File (`version.txt`)
- **Format**: Plain text (UTF-8)
- **Content**: `"V1"`

---

### 2.2 Notes Database (`notes_db.json`)
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
| `todoProgress` | `TodoProgress` | Nested object tracking checklist item completion |
| `media` | `List<MediaItem>` | Array of attached media items |
| `stickers` | `List<ReactionSticker>` | Array of placed reaction stickers and emojis |
| `theme` | `NoteTheme?` | Per-note theme customization object (or `null`) |
| `version` | `Long` | CRDT LWW version counter for conflict resolution |
| `deleted` | `Boolean` | Soft-delete tombstone status flag |

#### Sub-structures in `NoteEntity`:

##### `TodoProgress`
- `total` (`Int`): Total count of todo list items.
- `completed` (`Int`): Count of finished todo list items.

##### `MediaItem`
- `id` (`String`): Unique UUID identifier for the media item.
- `type` (`MediaItemType` Enum String): Type of media (`PHOTO`, `AUDIO`, `VIDEO`, `DOCUMENT`, `DRAWING`, `LINK`).
- `uri` (`String`): Local path or URI to the media asset.
- `metadata` (`MediaMetadata` Object):
  - `title` (`String?`): Optional display title.
  - `durationSec` (`Long?`): Duration in seconds for audio/video assets.
  - `aspectRatio` (`Float?`): Image/video aspect ratio.
  - `thumbnailUri` (`String?`): Path/URI to thumbnail asset.
  - `fileName` (`String?`): Original file name.
  - `fileSize` (`Long?`): Asset size in bytes.
  - `mimeType` (`String?`): MIME type string.

##### `ReactionSticker`
- `id` (`String`): Unique UUID identifier for the sticker.
- `stickerType` (`StickerType` Enum String): Sticker icon type (`JOY`, `HURT`, `FRUSTRATED`, `AMAZED`, `EXCITED`, `TOUCHED`, `ANXIOUS`, `BREWING`, `SEED`, `POLISHED`, `ACTION_REQUIRED`, `PRIVATE_PERSONAL`, `SEEN`, `NEEDS_DISCUSSION`, `LOVED`, `AGREED`, `STAR`, `SPARK`, `PIN`, `HEART`, `COFFEE`, `FIRE`, `LAUGH`, `FLOWER`, `MOON`, `THUMBS_UP`, `CLAP`, `CROWN`, `CUSTOM_SVG`).
- `position` (`OffsetPosition` Object): `{ "x": Float, "y": Float }` coordinates on note canvas.
- `placedBy` (`String`): User or device ID that placed the reaction.
- `timestamp` (`Long`): Epoch timestamp (ms) when sticker was added.

##### `NoteTheme`
- `dominantColorsHex` (`List<String>`): Array of extracted Hex color strings.
- `backgroundColorHex` (`String`): Hex color code for background.
- `surfaceColorHex` (`String`): Hex color code for card surfaces.
- `textColorHex` (`String`): Hex color code for body text.
- `accentColorHex` (`String`): Hex color code for primary accents.
- `customImageUri` (`String?`): Source image path for color extraction.
- `backgroundImageUri` (`String?`): Path to background wallpaper image.
- `backgroundBlurRadius` (`Float`): Blur radius in DP (0..25).
- `selectedPreset` (`ThemePreset` Enum String): Theme preset name (`DEFAULT_LIGHT`, `DEFAULT_DARK`, `AMOLED_BLACK`, `SUNSET_WARMTH`, `LAVENDER_DREAM`, `FOREST_MIST`, `OCEAN_BREEZE`, `LIGHT_GLASS`, `DARK_GLASS`, `SHINING_STARS_GLASS`, `CUSTOM_IMAGE`).

---

### 2.3 App Preferences & Settings (`settings.json`)
- **Format**: JSON Object of `AppSettings`.
- **Source**: `ThemePreferences` (`SharedPreferences` storage).

#### `AppSettings` Schema
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

---

### 2.4 Media Blob Files (`media/`)
- **Format**: Raw binary files copied directly from `context.filesDir/mucephei_media`.
- **Types**: Photos (`.jpg`/`.png`), Audio (`.mp4`/`.aac`), Videos (`.mp4`), Sketches (`drawing_*.png`), Documents (`.pdf`/`.txt`/etc.).

---

### 2.5 OpenVPN Profiles (`vpn_profiles/`)
- **Format**: OpenVPN configuration files copied from `context.filesDir/mucephi_vpn`.
- **Extension**: `.ovpn` files.

---

### 2.6 Custom Wallpapers (`wallpapers/`)
- **Format**: Image files copied from `context.filesDir/wallpapers`.
- **Types**: User-selected custom wallpaper images.

---

## 3. Encryption Specification (AES-256-GCM)

When user chooses encrypted export (`encrypt = true`):
- **Algorithm**: `AES-256-GCM` (`AES/GCM/NoPadding`)
- **Key Derivation**: `PBKDF2WithHmacSHA256`
  - Iterations: `10,000`
  - Salt Length: `16 bytes` (cryptographically random per export)
  - Key Length: `256 bits` (32 bytes)
- **Initialization Vector (IV)**: `12 bytes` (cryptographically random per export)
- **Authentication Tag**: `128 bits`
- **Binary Encrypted File Layout**:
  ```
  [0..15 bytes]  : Salt (16 bytes)
  [16..27 bytes] : IV (12 bytes)
  [28..N bytes]  : AES-256-GCM Encrypted Zip Ciphertext (with 16-byte auth tag)
  ```
