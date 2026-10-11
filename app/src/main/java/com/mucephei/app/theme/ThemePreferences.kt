package com.mucephei.app.theme

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.mucephei.app.data.model.NoteTheme
import com.mucephei.app.data.model.ThemePreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("mu_cephei_theme_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_PRESET = "theme_preset"
        private const val KEY_CUSTOM_THEME_JSON = "custom_theme_json"
        private const val KEY_WALLPAPER_PATH = "wallpaper_path"
        private const val KEY_BLUR_RADIUS = "blur_radius"
        private const val KEY_WALLPAPER_ALPHA = "wallpaper_alpha"
        private const val KEY_AUTO_NIGHT_MODE = "auto_night_mode"
        private const val KEY_FONT_SCALE = "font_scale"
        private const val KEY_IS_GRID_VIEW = "is_grid_view"
        private const val KEY_VECTOR_OPACITY = "vector_opacity"
        private const val KEY_HIDE_VECTORS = "hide_vectors"
        private const val KEY_TEXT_TRANSPARENCY_ENABLED = "text_transparency_enabled"
        private const val KEY_TEXT_TRANSPARENCY_ALPHA = "text_transparency_alpha"
        private const val KEY_GLASSIFY_ENABLED = "glassify_enabled"
        private const val KEY_LAST_HOST_IP = "last_host_ip"
        private const val KEY_CUSTOM_ROOM_CODE = "custom_room_code"
        private const val KEY_LAST_ROOM_CODE = "last_room_code"
    }

    var selectedPreset: ThemePreset
        get() {
            val name = prefs.getString(KEY_PRESET, ThemePreset.SUNSET_WARMTH.name)
            return try {
                ThemePreset.valueOf(name ?: ThemePreset.SUNSET_WARMTH.name)
            } catch (e: Exception) {
                ThemePreset.SUNSET_WARMTH
            }
        }
        set(value) {
            prefs.edit().putString(KEY_PRESET, value.name).apply()
        }

    @Volatile
    private var cachedCustomTheme: NoteTheme? = null
    @Volatile
    private var isCustomThemeLoaded: Boolean = false

    var customTheme: NoteTheme?
        get() {
            if (isCustomThemeLoaded) return cachedCustomTheme
            val json = prefs.getString(KEY_CUSTOM_THEME_JSON, null)
            if (json == null) {
                isCustomThemeLoaded = true
                cachedCustomTheme = null
                return null
            }
            return try {
                val theme = gson.fromJson(json, NoteTheme::class.java)
                cachedCustomTheme = theme
                isCustomThemeLoaded = true
                theme
            } catch (e: Exception) {
                isCustomThemeLoaded = true
                cachedCustomTheme = null
                null
            }
        }
        set(value) {
            cachedCustomTheme = value
            isCustomThemeLoaded = true
            if (value == null) {
                prefs.edit().remove(KEY_CUSTOM_THEME_JSON).apply()
            } else {
                prefs.edit().putString(KEY_CUSTOM_THEME_JSON, gson.toJson(value)).apply()
            }
        }

    private val _wallpaperFlow = MutableStateFlow(prefs.getString(KEY_WALLPAPER_PATH, null))
    val wallpaperFlow: StateFlow<String?> = _wallpaperFlow.asStateFlow()

    var wallpaperPath: String?
        get() = prefs.getString(KEY_WALLPAPER_PATH, null)
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_WALLPAPER_PATH).apply()
            } else {
                prefs.edit().putString(KEY_WALLPAPER_PATH, value).apply()
            }
            _wallpaperFlow.value = value
        }

    var blurRadius: Float
        get() = prefs.getFloat(KEY_BLUR_RADIUS, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_BLUR_RADIUS, value).apply()
        }

    var wallpaperAlpha: Float
        get() = prefs.getFloat(KEY_WALLPAPER_ALPHA, 0f)
        set(value) {
            prefs.edit().putFloat(KEY_WALLPAPER_ALPHA, value).apply()
        }

    var autoNightModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_NIGHT_MODE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_NIGHT_MODE, value).apply()
        }

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_FONT_SCALE, value).apply()
        }

    var isGridView: Boolean
        get() = prefs.getBoolean(KEY_IS_GRID_VIEW, false)
        set(value) {
            prefs.edit().putBoolean(KEY_IS_GRID_VIEW, value).apply()
        }

    var vectorOpacity: Float
        get() = prefs.getFloat(KEY_VECTOR_OPACITY, 0.75f)
        set(value) {
            prefs.edit().putFloat(KEY_VECTOR_OPACITY, value).apply()
        }

    var hideVectors: Boolean
        get() = prefs.getBoolean(KEY_HIDE_VECTORS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_VECTORS, value).apply()
        }

    var textTransparencyEnabled: Boolean
        get() = prefs.getBoolean(KEY_TEXT_TRANSPARENCY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TEXT_TRANSPARENCY_ENABLED, value).apply()
        }

    var textTransparencyAlpha: Float
        get() = prefs.getFloat(KEY_TEXT_TRANSPARENCY_ALPHA, 0.35f)
        set(value) {
            prefs.edit().putFloat(KEY_TEXT_TRANSPARENCY_ALPHA, value).apply()
        }

    var glassifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLASSIFY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_GLASSIFY_ENABLED, value).apply()
        }

    var lastHostIp: String
        get() = prefs.getString(KEY_LAST_HOST_IP, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LAST_HOST_IP, value).apply()
        }

    var customRoomCode: String
        get() = prefs.getString(KEY_CUSTOM_ROOM_CODE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_CUSTOM_ROOM_CODE, value).apply()
        }

    var lastRoomCode: String
        get() = prefs.getString(KEY_LAST_ROOM_CODE, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_LAST_ROOM_CODE, value).apply()
        }
}
