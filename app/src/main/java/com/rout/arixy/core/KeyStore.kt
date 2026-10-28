package com.rout.arixy.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Stores the user's own Gemini API key encrypted on-device, plus ARIXY settings.
 * Falls back to plain SharedPreferences if the device keystore is unavailable.
 */
object KeyStore {
    private const val FILE = "arixy_secure_prefs"
    private var prefs: SharedPreferences? = null

    @Synchronized
    private fun p(context: Context): SharedPreferences {
        prefs?.let { return it }
        val created = try {
            EncryptedSharedPreferences.create(
                FILE,
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                context.applicationContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
        prefs = created
        return created
    }

    fun getApiKey(context: Context): String = p(context).getString("gemini_api_key", "") ?: ""
    fun setApiKey(context: Context, key: String) { p(context).edit().putString("gemini_api_key", key.trim()).apply() }

    fun getModel(context: Context): String = p(context).getString("model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
    fun setModel(context: Context, model: String) { p(context).edit().putString("model", model.trim()).apply() }

    fun isTtsEnabled(context: Context): Boolean = p(context).getBoolean("tts_enabled", true)
    fun setTtsEnabled(context: Context, on: Boolean) { p(context).edit().putBoolean("tts_enabled", on).apply() }

    fun isWakeWordEnabled(context: Context): Boolean = p(context).getBoolean("wake_word_enabled", false)
    fun setWakeWordEnabled(context: Context, on: Boolean) { p(context).edit().putBoolean("wake_word_enabled", on).apply() }

    fun isGhostModeEnabled(context: Context): Boolean = p(context).getBoolean("ghost_mode_enabled", false)
    fun setGhostModeEnabled(context: Context, on: Boolean) { p(context).edit().putBoolean("ghost_mode_enabled", on).apply() }

    fun getGhostReplyText(context: Context): String =
        p(context).getString("ghost_reply_text", "Boss abhi busy hain, thodi der mein reply karenge. - ARIXY AI")
            ?: "Boss abhi busy hain, thodi der mein reply karenge. - ARIXY AI"
    fun setGhostReplyText(context: Context, text: String) { p(context).edit().putString("ghost_reply_text", text).apply() }

    fun isOrbEnabled(context: Context): Boolean = p(context).getBoolean("orb_enabled", false)
    fun setOrbEnabled(context: Context, on: Boolean) { p(context).edit().putBoolean("orb_enabled", on).apply() }

    /** Raw prefs handle for feature stores (e.g. MemoryManager). */
    fun raw(context: Context): SharedPreferences = p(context)

    fun getUserName(context: Context): String = p(context).getString("user_name", "") ?: ""
    fun setUserName(context: Context, name: String) { p(context).edit().putString("user_name", name.trim().take(40)).apply() }

    fun getPersonality(context: Context): PersonalityMode =
        PersonalityMode.values().firstOrNull { it.name == p(context).getString("personality_mode", "") }
            ?: PersonalityMode.FRIENDLY
    fun setPersonality(context: Context, mode: PersonalityMode) { p(context).edit().putString("personality_mode", mode.name).apply() }

    fun getJealousy(context: Context): JealousyLevel =
        JealousyLevel.values().firstOrNull { it.name == p(context).getString("jealousy_level", "") }
            ?: JealousyLevel.OFF
    fun setJealousy(context: Context, level: JealousyLevel) { p(context).edit().putString("jealousy_level", level.name).apply() }

    fun getSpeechRate(context: Context): Float = p(context).getFloat("speech_rate", 1.05f)
    fun setSpeechRate(context: Context, rate: Float) { p(context).edit().putFloat("speech_rate", rate.coerceIn(0.6f, 1.6f)).apply() }

    fun getVoicePitch(context: Context): Float = p(context).getFloat("voice_pitch", 1.08f)
    fun setVoicePitch(context: Context, pitch: Float) { p(context).edit().putFloat("voice_pitch", pitch.coerceIn(0.6f, 1.6f)).apply() }
}
