package com.pwdongle.recorder

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

// Extension property for DataStore
private const val PREFERENCES_NAME = "pwdongle_prefs"
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = PREFERENCES_NAME
)

/**
 * Manages app preferences using DataStore
 * 
 * Preferences:
 * - autoConnectEnabled: Whether to automatically search for and connect to PWDongle
 * - lastConnectedDevice: Name of the last successfully connected device
 */
class PreferencesManager(private val context: Context) {
    
    companion object {
        private val AUTO_CONNECT_ENABLED = booleanPreferencesKey("auto_connect_enabled")
        private val LAST_CONNECTED_DEVICE = stringPreferencesKey("last_connected_device")
        private val SAVED_DEVICES = stringPreferencesKey("saved_devices")
        private val THEME_MODE = stringPreferencesKey("theme_mode") // values: system, light, dark
    }
    
    /**
     * Flow of auto-connect preference (default: true)
     */
    val autoConnectEnabledFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[AUTO_CONNECT_ENABLED] ?: true
    }
    
    /**
     * Flow of last connected device name
     */
    val lastConnectedDeviceFlow: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[LAST_CONNECTED_DEVICE]
    }
    
    /**
     * Flow of saved devices (comma-separated)
     */
    val savedDevicesFlow: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val devicesStr = preferences[SAVED_DEVICES] ?: ""
        if (devicesStr.isEmpty()) emptyList() else devicesStr.split(",")
    }

    /**
     * Flow of theme mode (default: system)
     */
    val themeModeFlow: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: "system"
    }

    /**
     * Get current theme mode once (blocking caller when used with runBlocking)
     */
    suspend fun getThemeMode(): String {
        return context.dataStore.data.map { preferences ->
            preferences[THEME_MODE] ?: "system"
        }.first()
    }
    
    /**
     * Set auto-connect preference
     */
    suspend fun setAutoConnectEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CONNECT_ENABLED] = enabled
        }
    }
    
    /**
     * Save last connected device
     */
    suspend fun setLastConnectedDevice(deviceName: String) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CONNECTED_DEVICE] = deviceName
        }
    }
    
    /**
     * Clear last connected device
     */
    suspend fun clearLastConnectedDevice() {
        context.dataStore.edit { preferences ->
            preferences.remove(LAST_CONNECTED_DEVICE)
        }
    }
    
    /**
     * Add device to saved list
     */
    suspend fun addSavedDevice(deviceName: String) {
        context.dataStore.edit { preferences ->
            val currentDevices = preferences[SAVED_DEVICES]?.split(",")?.toMutableList() ?: mutableListOf()
            if (!currentDevices.contains(deviceName)) {
                currentDevices.add(deviceName)
                preferences[SAVED_DEVICES] = currentDevices.joinToString(",")
            }
        }
    }
    
    /**
     * Remove device from saved list
     */
    suspend fun removeSavedDevice(deviceName: String) {
        context.dataStore.edit { preferences ->
            val currentDevices = preferences[SAVED_DEVICES]?.split(",")?.toMutableList() ?: mutableListOf()
            currentDevices.remove(deviceName)
            preferences[SAVED_DEVICES] = currentDevices.joinToString(",")
        }
    }

    /**
     * Set theme mode (system, light, dark)
     */
    suspend fun setThemeMode(mode: String) {
        val normalized = when (mode) {
            "light", "dark", "system" -> mode
            else -> "system"
        }
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = normalized
        }
    }
}
