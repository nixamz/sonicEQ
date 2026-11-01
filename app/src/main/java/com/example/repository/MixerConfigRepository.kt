package com.example.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.FilterType
import com.example.model.MixerConfig
import com.example.model.ParametricBand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sonic_mixer_settings")

/**
 * Repository to persist and restore the entire mixer state (all 60 EQ bands, faders,
 * delay parameters, panning) using Jetpack DataStore.
 */
class MixerConfigRepository(private val context: Context) {

    companion object {
        private val KEY_SAVED_CONFIG = stringPreferencesKey("saved_mixer_config_json")
        private val KEY_ACTIVE_CONFIG = stringPreferencesKey("active_mixer_config_json")
    }

    val savedConfigFlow: Flow<MixerConfig?> = context.dataStore.data.map { preferences ->
        preferences[KEY_SAVED_CONFIG]?.let { deserializeConfig(it) }
    }

    val activeConfigFlow: Flow<MixerConfig> = context.dataStore.data.map { preferences ->
        preferences[KEY_ACTIVE_CONFIG]?.let { deserializeConfig(it) } ?: MixerConfig()
    }

    suspend fun saveActiveConfig(config: MixerConfig) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ACTIVE_CONFIG] = serializeConfig(config)
        }
    }

    suspend fun saveToPermanentSlot(config: MixerConfig) {
        context.dataStore.edit { preferences ->
            val json = serializeConfig(config)
            preferences[KEY_SAVED_CONFIG] = json
            preferences[KEY_ACTIVE_CONFIG] = json
        }
    }

    fun serializeConfig(config: MixerConfig): String {
        val root = JSONObject()
        root.put("name", config.name)
        root.put("preGainDb", config.preGainDb.toDouble())
        root.put("pan", config.pan.toDouble())
        root.put("mainInputGainDb", config.mainInputGainDb.toDouble())
        root.put("mainOutputGainDb", config.mainOutputGainDb.toDouble())
        root.put("lowFadeDb", config.lowFadeDb.toDouble())
        root.put("midFadeDb", config.midFadeDb.toDouble())
        root.put("highFadeDb", config.highFadeDb.toDouble())
        root.put("crossoverLowFreq", config.crossoverLowFreq.toDouble())
        root.put("crossoverHighFreq", config.crossoverHighFreq.toDouble())
        root.put("delayTimeMs", config.delayTimeMs.toDouble())
        root.put("delayFeedback", config.delayFeedback.toDouble())
        root.put("delayMix", config.delayMix.toDouble())
        root.put("delayEnabled", config.delayEnabled)
        root.put("eqEnabled", config.eqEnabled)
        root.put("limiterEnabled", config.limiterEnabled)

        val bandsArray = JSONArray()
        for (band in config.bands) {
            val bandObj = JSONObject()
            bandObj.put("id", band.id)
            bandObj.put("frequency", band.frequency.toDouble())
            bandObj.put("gainDb", band.gainDb.toDouble())
            bandObj.put("q", band.q.toDouble())
            bandObj.put("filterType", band.filterType.name)
            bandObj.put("isEnabled", band.isEnabled)
            bandsArray.put(bandObj)
        }
        root.put("bands", bandsArray)

        return root.toString()
    }

    fun deserializeConfig(jsonString: String): MixerConfig {
        return try {
            val root = JSONObject(jsonString)
            val name = root.optString("name", "Custom Preset")
            val preGainDb = root.optDouble("preGainDb", 0.0).toFloat()
            val pan = root.optDouble("pan", 0.0).toFloat()
            val mainInputGainDb = root.optDouble("mainInputGainDb", 0.0).toFloat()
            val mainOutputGainDb = root.optDouble("mainOutputGainDb", 0.0).toFloat()
            val lowFadeDb = root.optDouble("lowFadeDb", 0.0).toFloat()
            val midFadeDb = root.optDouble("midFadeDb", 0.0).toFloat()
            val highFadeDb = root.optDouble("highFadeDb", 0.0).toFloat()
            val crossoverLowFreq = root.optDouble("crossoverLowFreq", 250.0).toFloat()
            val crossoverHighFreq = root.optDouble("crossoverHighFreq", 4000.0).toFloat()
            val delayTimeMs = root.optDouble("delayTimeMs", 240.0).toFloat()
            val delayFeedback = root.optDouble("delayFeedback", 0.35).toFloat()
            val delayMix = root.optDouble("delayMix", 0.25).toFloat()
            val delayEnabled = root.optBoolean("delayEnabled", true)
            val eqEnabled = root.optBoolean("eqEnabled", true)
            val limiterEnabled = root.optBoolean("limiterEnabled", true)

            val bandsList = mutableListOf<ParametricBand>()
            val bandsArray = root.optJSONArray("bands")
            if (bandsArray != null) {
                for (i in 0 until bandsArray.length()) {
                    val bandObj = bandsArray.getJSONObject(i)
                    val id = bandObj.optInt("id", i + 1)
                    val freq = bandObj.optDouble("frequency", 1000.0).toFloat()
                    val gain = bandObj.optDouble("gainDb", 0.0).toFloat()
                    val q = bandObj.optDouble("q", 1.414).toFloat()
                    val typeName = bandObj.optString("filterType", FilterType.PEAK.name)
                    val type = try { FilterType.valueOf(typeName) } catch (_: Exception) { FilterType.PEAK }
                    val isEnabled = bandObj.optBoolean("isEnabled", true)

                    bandsList.add(
                        ParametricBand(
                            id = id,
                            frequency = freq,
                            gainDb = gain,
                            q = q,
                            filterType = type,
                            isEnabled = isEnabled
                        )
                    )
                }
            }

            MixerConfig(
                name = name,
                preGainDb = preGainDb,
                pan = pan,
                mainInputGainDb = mainInputGainDb,
                mainOutputGainDb = mainOutputGainDb,
                lowFadeDb = lowFadeDb,
                midFadeDb = midFadeDb,
                highFadeDb = highFadeDb,
                crossoverLowFreq = crossoverLowFreq,
                crossoverHighFreq = crossoverHighFreq,
                delayTimeMs = delayTimeMs,
                delayFeedback = delayFeedback,
                delayMix = delayMix,
                delayEnabled = delayEnabled,
                eqEnabled = eqEnabled,
                limiterEnabled = limiterEnabled,
                bands = if (bandsList.isNotEmpty()) bandsList else MixerConfig.defaultBands(10)
            )
        } catch (_: Exception) {
            MixerConfig()
        }
    }
}
