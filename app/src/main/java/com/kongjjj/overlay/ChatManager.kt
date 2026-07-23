package com.kongjjj.overlay

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class ChatManager private constructor(context: Context) {
    private val scope = CoroutineScope(Dispatchers.Main)
    
    val twitchClient = TwitchChatClient()
    val youtubeClient = YouTubeChatClient()
    val emoteRepository = EmoteRepository()
    val ttsManager = TtsManager(context)
    
    // Settings state
    val twitchChannel = MutableStateFlow("")
    val youtubeChannelId = MutableStateFlow("")
    val chatFontSize = MutableStateFlow(DEFAULT_FONT_SIZE)
    val chatLineSpacing = MutableStateFlow(DEFAULT_LINE_SPACING)
    val chatEmoteSize = MutableStateFlow(DEFAULT_EMOTE_SIZE)
    val chatUsernameSize = MutableStateFlow(DEFAULT_USERNAME_SIZE)
    val animatedEmotes = MutableStateFlow(true)
    val enable7tv = MutableStateFlow(true)
    val enableBttv = MutableStateFlow(true)
    val enableFfz = MutableStateFlow(true)
    val backgroundColor = MutableStateFlow("transparent") // "transparent" or "black"
    val appLanguage = MutableStateFlow("zh-TW") // "zh-TW", "en", "ja"
    val showTimestamp = MutableStateFlow(false)
    
    // TTS Settings
    val ttsEnabled = MutableStateFlow(false)
    val ttsIgnoreSender = MutableStateFlow(false)
    val ttsLanguage = MutableStateFlow("zh-HK") // Default to Cantonese

    private val spokenMessageIds = mutableSetOf<String>()

    init {
        // Load settings from SharedPreferences
        val prefs = context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        twitchChannel.value = prefs.getString("twitch_channel", "") ?: ""
        youtubeChannelId.value = prefs.getString("youtube_channel_id", "") ?: ""
        chatFontSize.value = prefs.getFloat("chat_font_size", DEFAULT_FONT_SIZE)
        chatLineSpacing.value = prefs.getFloat("chat_line_spacing", DEFAULT_LINE_SPACING)
        chatEmoteSize.value = prefs.getFloat("chat_emote_size", DEFAULT_EMOTE_SIZE)
        chatUsernameSize.value = prefs.getFloat("chat_username_size", DEFAULT_USERNAME_SIZE)
        animatedEmotes.value = prefs.getBoolean("animated_emotes", true)
        enable7tv.value = prefs.getBoolean("enable_7tv", true)
        enableBttv.value = prefs.getBoolean("enable_bttv", true)
        enableFfz.value = prefs.getBoolean("enable_ffz", true)
        backgroundColor.value = prefs.getString("background_color", "transparent") ?: "transparent"
        appLanguage.value = prefs.getString("app_language", "zh-TW") ?: "zh-TW"
        showTimestamp.value = prefs.getBoolean("show_timestamp", false)
        
        ttsEnabled.value = prefs.getBoolean("tts_enabled", false)
        ttsIgnoreSender.value = prefs.getBoolean("tts_ignore_sender", false)
        ttsLanguage.value = prefs.getString("tts_language", "zh-HK") ?: "zh-HK"

        // Set initial TTS language
        updateTtsLanguage(ttsLanguage.value)

        // Observe new messages for TTS
        scope.launch {
            twitchClient.newMessages.collect { message ->
                if (ttsEnabled.value) speakMessage(message)
            }
        }
        scope.launch {
            youtubeClient.newMessages.collect { message ->
                if (ttsEnabled.value) speakMessage(message)
            }
        }
        
        // Load emotes
        scope.launch {
            emoteRepository.loadAll(enable7tv.value, enableBttv.value, enableFfz.value)
        }
    }

    private fun speakMessage(message: ChatMessage) {
        if (message.id == "system_instruction") return
        if (spokenMessageIds.contains(message.id)) return
        
        spokenMessageIds.add(message.id)
        if (spokenMessageIds.size > 200) {
            // Remove some old IDs to keep the set small
            val toRemove = spokenMessageIds.take(100)
            spokenMessageIds.removeAll(toRemove.toSet())
        }

        val textToSpeak = if (ttsIgnoreSender.value) {
            message.message
        } else {
            "${message.username}說: ${message.message}"
        }
        
        // Clean up message for TTS (simple link replacement)
        val cleanedText = textToSpeak.replace(Regex("https?://\\S+"), "連結")
        
        ttsManager.speak(cleanedText)
    }

    private fun updateTtsLanguage(langCode: String) {
        val locale = when (langCode) {
            "zh-HK" -> Locale.forLanguageTag("zh-HK") // Cantonese
            "zh-TW" -> Locale.TAIWAN     // Mandarin (TW)
            "zh-CN" -> Locale.CHINA      // Mandarin (CN)
            "en-US" -> Locale.US         // English
            "ja-JP" -> Locale.JAPAN      // Japanese
            else -> Locale.forLanguageTag("zh-HK")
        }
        ttsManager.setLanguage(locale)
    }

    fun connect() {
        if (twitchChannel.value.isNotEmpty()) {
            twitchClient.connect(twitchChannel.value)
        } else {
            twitchClient.disconnect()
        }
        if (youtubeChannelId.value.isNotEmpty()) {
            youtubeClient.connect(youtubeChannelId.value)
        } else {
            youtubeClient.disconnect()
        }
    }

    fun saveTwitchChannel(channel: String, context: Context) {
        twitchChannel.value = channel
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putString("twitch_channel", channel) }
        if (channel.isNotEmpty()) {
            twitchClient.connect(channel)
        } else {
            twitchClient.disconnect()
        }
    }

    fun saveYoutubeChannelId(channelId: String, context: Context) {
        youtubeChannelId.value = channelId
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putString("youtube_channel_id", channelId) }
        if (channelId.isNotEmpty()) {
            youtubeClient.connect(channelId)
        } else {
            youtubeClient.disconnect()
        }
    }

    fun saveFontSize(size: Float, context: Context) {
        chatFontSize.value = size
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("chat_font_size", size) }
    }

    fun saveLineSpacing(spacing: Float, context: Context) {
        chatLineSpacing.value = spacing
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("chat_line_spacing", spacing) }
    }

    fun saveEmoteSize(size: Float, context: Context) {
        chatEmoteSize.value = size
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("chat_emote_size", size) }
    }

    fun saveUsernameSize(size: Float, context: Context) {
        chatUsernameSize.value = size
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("chat_username_size", size) }
    }

    fun saveAnimatedEmotes(enabled: Boolean, context: Context) {
        animatedEmotes.value = enabled
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("animated_emotes", enabled) }
    }

    fun saveEnable7tv(enabled: Boolean, context: Context) {
        enable7tv.value = enabled
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("enable_7tv", enabled) }
        scope.launch { reloadEmotes() }
    }

    fun saveEnableBttv(enabled: Boolean, context: Context) {
        enableBttv.value = enabled
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("enable_bttv", enabled) }
        scope.launch { reloadEmotes() }
    }

    fun saveEnableFfz(enabled: Boolean, context: Context) {
        enableFfz.value = enabled
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("enable_ffz", enabled) }
        scope.launch { reloadEmotes() }
    }

    fun saveBackgroundColor(color: String, context: Context) {
        backgroundColor.value = color
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putString("background_color", color) }
    }

    fun saveAppLanguage(lang: String, context: Context) {
        appLanguage.value = lang
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putString("app_language", lang) }
    }

    fun saveShowTimestamp(show: Boolean, context: Context) {
        showTimestamp.value = show
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("show_timestamp", show) }
    }

    fun saveTtsEnabled(enabled: Boolean, context: Context) {
        ttsEnabled.value = enabled
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("tts_enabled", enabled) }
    }

    fun saveTtsLanguage(langCode: String, context: Context) {
        ttsLanguage.value = langCode
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putString("tts_language", langCode) }
        updateTtsLanguage(langCode)
    }

    fun saveTtsIgnoreSender(ignore: Boolean, context: Context) {
        ttsIgnoreSender.value = ignore
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("tts_ignore_sender", ignore) }
    }
    
    private suspend fun reloadEmotes() {
        emoteRepository.loadAll(enable7tv.value, enableBttv.value, enableFfz.value)
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearChatCache(context: Context) {
        twitchClient.clearMessages()
        youtubeClient.clearMessages()
        // Clear Coil cache
        val imageLoader = coil.Coil.imageLoader(context)
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }

    companion object {
        @Volatile
        private var INSTANCE: ChatManager? = null

        fun getInstance(context: Context): ChatManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ChatManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
