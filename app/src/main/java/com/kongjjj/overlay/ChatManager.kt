package com.kongjjj.overlay

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.seconds

class ChatManager private constructor(private val context: Context) {
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
    val textShadow = MutableStateFlow(DEFAULT_TEXT_SHADOW)
    val shadowRadius = MutableStateFlow(DEFAULT_SHADOW_RADIUS)
    val shadowOffsetX = MutableStateFlow(DEFAULT_SHADOW_OFFSET_X)
    val shadowOffsetY = MutableStateFlow(DEFAULT_SHADOW_OFFSET_Y)
    val animatedEmotes = MutableStateFlow(true)
    val enable7tv = MutableStateFlow(true)
    val enableBttv = MutableStateFlow(true)
    val enableFfz = MutableStateFlow(true)
    val backgroundColor = MutableStateFlow("transparent") // "transparent" or "black"
    val appLanguage = MutableStateFlow("zh-TW") // "zh-TW", "en", "ja"
    val showTimestamp = MutableStateFlow(false)
    val showStreamInfo = MutableStateFlow(DEFAULT_SHOW_STREAM_INFO)

    // Stream Info state
    val viewersCount = MutableStateFlow(0)
    val streamStartTime = MutableStateFlow<Long?>(null)
    val uptimeText = MutableStateFlow("")

    private var viewerUpdateJob: Job? = null
    private var uptimeUpdateJob: Job? = null
    private val httpClient = OkHttpClient()

    private val _systemMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val systemMessages: StateFlow<List<ChatMessage>> = _systemMessages.asStateFlow()

    // TTS Settings
    val ttsEnabled = MutableStateFlow(false)
    val ttsIgnoreSender = MutableStateFlow(false)
    val ttsLanguage = MutableStateFlow("zh-HK") // Default to Cantonese

    private val spokenMessageIds = mutableSetOf<String>()

    init {
        // Load settings from SharedPreferences
        val prefs = context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE)
        
        twitchClient.onReconnect = {
            addSystemMessage(context.getString(R.string.reconnecting_twitch))
        }
        youtubeClient.onReconnect = {
            addSystemMessage(context.getString(R.string.reconnecting_youtube))
        }

        twitchChannel.value = prefs.getString("twitch_channel", "") ?: ""
        youtubeChannelId.value = prefs.getString("youtube_channel_id", "") ?: ""
        chatFontSize.value = prefs.getFloat("chat_font_size", DEFAULT_FONT_SIZE)
        chatLineSpacing.value = prefs.getFloat("chat_line_spacing", DEFAULT_LINE_SPACING)
        chatEmoteSize.value = prefs.getFloat("chat_emote_size", DEFAULT_EMOTE_SIZE)
        chatUsernameSize.value = prefs.getFloat("chat_username_size", DEFAULT_USERNAME_SIZE)
        textShadow.value = prefs.getBoolean("text_shadow", DEFAULT_TEXT_SHADOW)
        shadowRadius.value = prefs.getFloat("shadow_radius", DEFAULT_SHADOW_RADIUS)
        shadowOffsetX.value = prefs.getFloat("shadow_offset_x", DEFAULT_SHADOW_OFFSET_X)
        shadowOffsetY.value = prefs.getFloat("shadow_offset_y", DEFAULT_SHADOW_OFFSET_Y)
        animatedEmotes.value = prefs.getBoolean("animated_emotes", true)
        enable7tv.value = prefs.getBoolean("enable_7tv", true)
        enableBttv.value = prefs.getBoolean("enable_bttv", true)
        enableFfz.value = prefs.getBoolean("enable_ffz", true)
        backgroundColor.value = prefs.getString("background_color", "transparent") ?: "transparent"
        appLanguage.value = prefs.getString("app_language", "zh-TW") ?: "zh-TW"
        showTimestamp.value = prefs.getBoolean("show_timestamp", false)
        showStreamInfo.value = prefs.getBoolean("show_stream_info", DEFAULT_SHOW_STREAM_INFO)
        
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

        // Start stream info updates
        startStreamInfoUpdates()
    }

    private fun startStreamInfoUpdates() {
        stopStreamInfoUpdates()
        viewerUpdateJob = scope.launch {
            while (isActive) {
                val channel = twitchChannel.value
                if (channel.isNotBlank() && channel != "yourchannel") {
                    var info: StreamInfo? = null
                    try {
                        info = withTimeout(5.seconds) {
                            withContext(Dispatchers.IO) { fetchStreamInfo(channel) }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatManager", "Failed to fetch stream info", e)
                    }

                    withContext(Dispatchers.Main) {
                        if (info != null) {
                            viewersCount.value = info.viewers
                            if (info.createdAt != streamStartTime.value) {
                                streamStartTime.value = info.createdAt
                                if (info.createdAt != null) {
                                    startUptimeUpdates(info.createdAt)
                                } else {
                                    stopUptimeUpdates()
                                }
                            }
                        } else {
                            // If request failed, keep current uptime but maybe clear viewers?
                            // User code keeps uptime but hides viewers count layout.
                            // We'll just keep values as is for now.
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        viewersCount.value = 0
                        streamStartTime.value = null
                        stopUptimeUpdates()
                    }
                }
                delay(15.seconds)
            }
        }
    }

    private fun stopStreamInfoUpdates() {
        viewerUpdateJob?.cancel()
        viewerUpdateJob = null
        stopUptimeUpdates()
    }

    private fun startUptimeUpdates(startTime: Long) {
        uptimeUpdateJob?.cancel()
        uptimeUpdateJob = scope.launch {
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= 0) {
                    val hours = elapsed / 3600000
                    val minutes = (elapsed % 3600000) / 60000
                    val seconds = (elapsed % 60000) / 1000
                    uptimeText.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
                }
                delay(1.seconds)
            }
        }
    }

    private fun stopUptimeUpdates() {
        uptimeUpdateJob?.cancel()
        uptimeUpdateJob = null
        uptimeText.value = ""
    }

    private fun fetchStreamInfo(channelName: String): StreamInfo {
        if (channelName.isBlank()) return StreamInfo(0, null)
        return try {
            val request = Request.Builder()
                .url("https://gql.twitch.tv/gql")
                .addHeader("Client-ID", "kimne78kx3ncx6brgo4mv6wki5h1ko")
                .addHeader("Content-Type", "application/json")
                .post(
                    """
                {
                    "query": "query { user(login: \"$channelName\") { stream { viewersCount createdAt } } }"
                }
                """.trimIndent().toRequestBody("application/json; charset=utf-8".toMediaType())
                )
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val json = JSONObject(response.body?.string() ?: "{}")
                val data = json.optJSONObject("data")
                val user = data?.optJSONObject("user")
                val stream = user?.optJSONObject("stream")
                val viewers = stream?.optInt("viewersCount", 0) ?: 0
                val createdAtStr = stream?.optString("createdAt")
                val createdAt = if (createdAtStr != null && createdAtStr != "null") {
                    try {
                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                        dateFormat.parse(createdAtStr)?.time
                    } catch (_: Exception) {
                        null
                    }
                } else null
                StreamInfo(viewers, createdAt)
            } else {
                StreamInfo(0, null)
            }
        } catch (e: Exception) {
            Log.e("ChatManager", "Error fetching stream info", e)
            StreamInfo(0, null)
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

    fun addSystemMessage(text: String) {
        // Check if message with same text already exists to avoid duplicates
        if (_systemMessages.value.any { it.message == text }) return

        val message = ChatMessage(
            id = java.util.UUID.randomUUID().toString(),
            username = "System",
            message = text,
            platform = "system",
            timestamp = System.currentTimeMillis()
        )
        // Add to system messages
        _systemMessages.value = (_systemMessages.value + message).takeLast(5)
        
        // Auto-remove after 7 seconds
        scope.launch {
            delay(7_000)
            _systemMessages.value = _systemMessages.value.filter { it.id != message.id }
        }
    }

    fun connect() {
        if (twitchChannel.value.isNotEmpty()) {
            addSystemMessage(context.getString(R.string.reconnecting_twitch))
            twitchClient.connect(twitchChannel.value)
        } else {
            twitchClient.disconnect()
        }
        if (youtubeChannelId.value.isNotEmpty()) {
            addSystemMessage(context.getString(R.string.reconnecting_youtube))
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

    fun saveTextShadow(enabled: Boolean, context: Context) {
        textShadow.value = enabled
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("text_shadow", enabled) }
    }

    fun saveShadowRadius(radius: Float, context: Context) {
        shadowRadius.value = radius
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("shadow_radius", radius) }
    }

    fun saveShadowOffsetX(offset: Float, context: Context) {
        shadowOffsetX.value = offset
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("shadow_offset_x", offset) }
    }

    fun saveShadowOffsetY(offset: Float, context: Context) {
        shadowOffsetY.value = offset
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putFloat("shadow_offset_y", offset) }
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

    fun saveShowStreamInfo(show: Boolean, context: Context) {
        showStreamInfo.value = show
        context.getSharedPreferences("OverlayPrefs", Context.MODE_PRIVATE).edit { putBoolean("show_stream_info", show) }
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
