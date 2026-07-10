package com.kongjjj.overlay

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID

class YouTubeChatClient {
    private val TAG = "YouTubeChatClient"
    private val http = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var job: Job? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _newMessages = MutableSharedFlow<ChatMessage>()
    val newMessages: SharedFlow<ChatMessage> = _newMessages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _viewerCount = MutableStateFlow<Int?>(null)
    val viewerCount: StateFlow<Int?> = _viewerCount

    private var currentChannelId: String? = null
    private var currentVideoId: String? = null
    private var apiKey: String? = null
    private var continuation: String? = null

    fun connect(channelId: String) {
        if (channelId == currentChannelId && _connected.value) return
        
        disconnect()
        currentChannelId = channelId
        _messages.value = emptyList()

        job = scope.launch {
            try {
                // If it's a channel ID (starts with UC), resolve it to a live video ID
                val videoId = if (channelId.startsWith("UC")) {
                    resolveLiveVideoId(channelId)
                } else {
                    channelId // Fallback for direct video ID
                }

                if (videoId != null && fetchInitialPage(videoId)) {
                    currentVideoId = videoId
                    _connected.value = true
                    pollChat()
                } else {
                    Log.e(TAG, "Failed to resolve or fetch initial page for: $channelId")
                    _connected.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to YouTube chat", e)
                _connected.value = false
            }
        }
    }

    private suspend fun resolveLiveVideoId(channelId: String): String? {
        val url = "https://www.youtube.com/channel/$channelId/live"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = http.newCall(request).execute()
            // YouTube redirects /live to the watch page. We want the final URL.
            val finalUrl = response.request.url.toString()
            
            // Extract v=VIDEO_ID from URL
            var videoId = Regex("[?&]v=([^&]+)").find(finalUrl)?.groupValues?.get(1)
            
            if (videoId == null) {
                // Try extracting from HTML if redirect didn't happen as expected
                val html = response.body?.string() ?: ""
                videoId = Regex("\"videoId\":\"([^\"]{11})\"").find(html)?.groupValues?.get(1)
            }
            
            videoId
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving live video ID", e)
            null
        }
    }

    fun disconnect() {
        job?.cancel()
        job = null
        _connected.value = false
        currentChannelId = null
        currentVideoId = null
        apiKey = null
        continuation = null
    }

    private suspend fun fetchInitialPage(videoId: String): Boolean {
        val url = "https://www.youtube.com/live_chat?v=$videoId"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()

        return try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) return false
            val html = response.body?.string() ?: ""

            apiKey = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)
            continuation = Regex("\"continuation\":\"([^\"]+)\"").find(html)?.groupValues?.get(1)

            apiKey != null && continuation != null
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching initial page", e)
            false
        }
    }

    private suspend fun pollChat() {
        while (job?.isActive == true && apiKey != null && continuation != null) {
            try {
                // Also poll viewer count while we're at it
                currentVideoId?.let { fetchViewerCount(it) }

                val url = "https://www.youtube.com/youtubei/v1/live_chat/get_live_chat?key=$apiKey"
                val json = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20210622.10.00")
                        })
                    })
                    put("continuation", continuation)
                }

                val request = Request.Builder()
                    .url(url)
                    .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .build()

                val response = http.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val jsonObj = JSONObject(body)
                    
                    val continuationData = jsonObj.optJSONObject("continuationContents")?.optJSONObject("liveChatContinuation")
                    continuation = continuationData?.optJSONArray("continuations")?.optJSONObject(0)
                        ?.optJSONObject("invalidationContinuationData")?.optString("continuation")
                        ?: continuationData?.optJSONArray("continuations")?.optJSONObject(0)
                        ?.optJSONObject("timedContinuationData")?.optString("continuation")

                    val actions = continuationData?.optJSONArray("actions")
                    if (actions != null) {
                        val newMessages = mutableListOf<ChatMessage>()
                        for (i in 0 until actions.length()) {
                            val action = actions.getJSONObject(i)
                            val item = action.optJSONObject("addChatItemAction")?.optJSONObject("item")
                            val textItem = item?.optJSONObject("liveChatTextMessageRenderer")
                            
                            if (textItem != null) {
                                val authorName = textItem.optJSONObject("authorName")?.optString("simpleText") ?: "Unknown"
                                
                                // Parse badges
                                val badgeTags = mutableListOf<String>()
                                val authorBadges = textItem.optJSONArray("authorBadges")
                                if (authorBadges != null) {
                                    for (j in 0 until authorBadges.length()) {
                                        val badge = authorBadges.getJSONObject(j).optJSONObject("liveChatAuthorBadgeRenderer")
                                        if (badge != null) {
                                            val customThumbnail = badge.optJSONObject("customThumbnail")
                                            if (customThumbnail != null) {
                                                // Try to get a higher resolution thumbnail if possible, or just the first one
                                                val thumbnails = customThumbnail.optJSONArray("thumbnails")
                                                val url = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")
                                                    ?: thumbnails?.optJSONObject(0)?.optString("url")
                                                if (url != null) {
                                                    // Ensure the URL is absolute
                                                    val absoluteUrl = if (url.startsWith("//")) "https:$url" else url
                                                    badgeTags.add(absoluteUrl)
                                                }
                                            } else {
                                                val icon = badge.optJSONObject("icon")
                                                val iconType = icon?.optString("iconType")
                                                if (iconType != null) {
                                                    // Map standard icons to tags that we can handle or URLs
                                                    badgeTags.add("yt-$iconType")
                                                }
                                            }
                                        }
                                    }
                                }

                                val messageParts = textItem.optJSONObject("message")?.optJSONArray("runs")
                                val messageText = StringBuilder()
                                val youtubeEmotes = mutableMapOf<String, String>()
                                if (messageParts != null) {
                                    for (j in 0 until messageParts.length()) {
                                        val run = messageParts.getJSONObject(j)
                                        if (run.has("text")) {
                                            messageText.append(run.optString("text"))
                                        } else if (run.has("emoji")) {
                                            val emoji = run.optJSONObject("emoji")
                                            val shortcut = emoji?.optJSONArray("shortcuts")?.optString(0) ?: ":emoji:"
                                            messageText.append(shortcut)
                                            
                                            val url = emoji?.optJSONObject("image")?.optJSONArray("thumbnails")?.optJSONObject(0)?.optString("url")
                                            if (url != null) {
                                                youtubeEmotes[shortcut] = url
                                            }
                                        }
                                    }
                                }

                                val timestampUsec = textItem.optString("timestampUsec").toLongOrNull() ?: 0L
                                
                                newMessages.add(ChatMessage(
                                    id = UUID.randomUUID().toString(),
                                    username = authorName,
                                    message = messageText.toString(),
                                    badgeTags = badgeTags,
                                    youtubeEmotes = youtubeEmotes,
                                    timestamp = timestampUsec / 1000,
                                    platform = "youtube"
                                ))
                            }
                        }
                        if (newMessages.isNotEmpty()) {
                            // Filter out messages we already have
                            val existingIds = _messages.value.map { it.id }.toSet()
                            val uniqueNewMessages = newMessages.filter { it.id !in existingIds }
                            
                            if (uniqueNewMessages.isNotEmpty()) {
                                _messages.value = (_messages.value + uniqueNewMessages).takeLast(MAX_CHAT_MESSAGES)
                                uniqueNewMessages.forEach { msg -> scope.launch { _newMessages.emit(msg) } }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error polling chat", e)
            }
            delay(5000) // Poll every 5 seconds to avoid rate limiting
        }
    }

    private suspend fun fetchViewerCount(videoId: String) {
        if (apiKey == null) return
        val url = "https://www.youtube.com/youtubei/v1/player?key=$apiKey"
        val json = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20210622.10.00")
                })
            })
            put("videoId", videoId)
        }
        val request = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaTypeOrNull()))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
            .build()
        try {
            val response = http.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                val jsonObj = JSONObject(body)
                val viewCount = jsonObj.optJSONObject("videoDetails")?.optString("viewCount")
                _viewerCount.value = viewCount?.toIntOrNull()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching viewer count", e)
        }
    }
}
