package com.example.core.datastore

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.auraDataStore: DataStore<Preferences> by preferencesDataStore(name = "aura_preferences")

data class PlaybackResumeState(
    val mediaId: Long = -1L,
    val positionMs: Long = 0L,
    val speed: Float = 1f,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false
)

data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.STUDIO_DARK,
    val dynamicColor: Boolean = false,
    val autosaveEditor: Boolean = true,
    val magneticSnapDefault: Boolean = true,
    val deviceAlias: String = "Aura-${Build.MODEL.take(14)}",
    val shareDownloadFolderUri: String? = null,
    val hasPrivatePin: Boolean = false,
    val pauseOnHeadphoneDisconnect: Boolean = true
)

@Singleton
class AuraPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTOSAVE_EDITOR = booleanPreferencesKey("autosave_editor")
        val MAGNETIC_SNAP = booleanPreferencesKey("magnetic_snap")
        val DEVICE_ALIAS = stringPreferencesKey("device_alias")
        val SHARE_FOLDER_URI = stringPreferencesKey("share_folder_uri")
        val PRIVATE_PIN_SALT = stringPreferencesKey("private_pin_salt")
        val PRIVATE_PIN_HASH = stringPreferencesKey("private_pin_hash")
        val PAUSE_ON_NOISY = booleanPreferencesKey("pause_on_noisy")

        val RESUME_MEDIA_ID = longPreferencesKey("resume_media_id")
        val RESUME_POSITION_MS = longPreferencesKey("resume_position_ms")
        val RESUME_SPEED = floatPreferencesKey("resume_speed")
        val RESUME_REPEAT_MODE = intPreferencesKey("resume_repeat_mode")
        val RESUME_SHUFFLE = booleanPreferencesKey("resume_shuffle")
    }

    val settingsFlow: Flow<AppSettingsState> = context.auraDataStore.data.map { prefs ->
        val mode = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.STUDIO_DARK.name)
        }.getOrDefault(ThemeMode.STUDIO_DARK)
        AppSettingsState(
            themeMode = mode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            autosaveEditor = prefs[Keys.AUTOSAVE_EDITOR] ?: true,
            magneticSnapDefault = prefs[Keys.MAGNETIC_SNAP] ?: true,
            deviceAlias = prefs[Keys.DEVICE_ALIAS] ?: "Aura-${Build.MODEL.take(14)}",
            shareDownloadFolderUri = prefs[Keys.SHARE_FOLDER_URI],
            hasPrivatePin = !prefs[Keys.PRIVATE_PIN_HASH].isNullOrBlank(),
            pauseOnHeadphoneDisconnect = prefs[Keys.PAUSE_ON_NOISY] ?: true
        )
    }

    val resumeStateFlow: Flow<PlaybackResumeState> = context.auraDataStore.data.map { prefs ->
        PlaybackResumeState(
            mediaId = prefs[Keys.RESUME_MEDIA_ID] ?: -1L,
            positionMs = prefs[Keys.RESUME_POSITION_MS] ?: 0L,
            speed = prefs[Keys.RESUME_SPEED] ?: 1f,
            repeatMode = prefs[Keys.RESUME_REPEAT_MODE] ?: 0,
            shuffleEnabled = prefs[Keys.RESUME_SHUFFLE] ?: false
        )
    }

    suspend fun getPinCredentials(): Pair<String, String>? {
        val prefs = context.auraDataStore.data.first()
        val salt = prefs[Keys.PRIVATE_PIN_SALT] ?: return null
        val hash = prefs[Keys.PRIVATE_PIN_HASH] ?: return null
        return salt to hash
    }

    suspend fun savePinCredentials(salt: String, hash: String) {
        context.auraDataStore.edit {
            it[Keys.PRIVATE_PIN_SALT] = salt
            it[Keys.PRIVATE_PIN_HASH] = hash
        }
    }

    suspend fun updatePlaybackResume(
        mediaId: Long,
        positionMs: Long,
        speed: Float,
        repeatMode: Int,
        shuffleEnabled: Boolean
    ) {
        context.auraDataStore.edit {
            it[Keys.RESUME_MEDIA_ID] = mediaId
            it[Keys.RESUME_POSITION_MS] = positionMs
            it[Keys.RESUME_SPEED] = speed
            it[Keys.RESUME_REPEAT_MODE] = repeatMode
            it[Keys.RESUME_SHUFFLE] = shuffleEnabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.auraDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.auraDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setAutosaveEditor(enabled: Boolean) {
        context.auraDataStore.edit { it[Keys.AUTOSAVE_EDITOR] = enabled }
    }

    suspend fun setMagneticSnapDefault(enabled: Boolean) {
        context.auraDataStore.edit { it[Keys.MAGNETIC_SNAP] = enabled }
    }

    suspend fun setDeviceAlias(alias: String) {
        context.auraDataStore.edit { it[Keys.DEVICE_ALIAS] = alias.trim().take(24).ifBlank { "Aura Device" } }
    }

    suspend fun setShareDownloadFolderUri(uri: String?) {
        context.auraDataStore.edit {
            if (uri == null) it.remove(Keys.SHARE_FOLDER_URI) else it[Keys.SHARE_FOLDER_URI] = uri
        }
    }

    suspend fun setPauseOnNoisy(enabled: Boolean) {
        context.auraDataStore.edit { it[Keys.PAUSE_ON_NOISY] = enabled }
    }
}
