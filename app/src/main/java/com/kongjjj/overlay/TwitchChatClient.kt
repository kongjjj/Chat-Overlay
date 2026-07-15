package com.kongjjj.overlay

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

class TwitchChatClient {
    private val TAG = "TwitchChatClient"
    private val http = OkHttpClient()
    private var socket: WebSocket? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _newMessages = MutableSharedFlow<ChatMessage>()
    val newMessages: SharedFlow<ChatMessage> = _newMessages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _roomId = MutableStateFlow("")
    val roomId: StateFlow<String> = _roomId

    private var currentChannel: String? = null
    private var lastReceivedTimestamp: Long? = null
    private var shouldBeConnected = false
    private val scope = CoroutineScope(Dispatchers.IO)

    fun connect(channel: String) {
        val normalizedChannel = channel.lowercase().trim()
        if (normalizedChannel == currentChannel && _connected.value) return

        shouldBeConnected = true
        if (normalizedChannel != currentChannel) {
            _messages.value = emptyList()
            lastReceivedTimestamp = null
        }
        
        disconnectInternal()
        currentChannel = normalizedChannel
        _roomId.value = ""

        val nick = "justinfan${(10000..99999).random()}"
        val req = Request.Builder().url("wss://irc-ws.chat.twitch.tv:443").build()

        socket = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                ws.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                ws.send("NICK $nick")
                ws.send("JOIN #$normalizedChannel")
                _connected.value = true
                
                // Fetch recent messages after connected
                scope.launch {
                    val recent = fetchRecentMessages(normalizedChannel)
                    if (recent.isNotEmpty()) {
                        // Merge with any real-time messages that arrived while fetching history
                        val current = _messages.value
                        val existingIds = current.map { it.id }.toSet()
                        val filteredRecent = recent.filter { it.id !in existingIds }
                        _messages.value = (filteredRecent + current).takeLast(MAX_CHAT_MESSAGES)
                    }
                }
            }

            override fun onMessage(ws: WebSocket, text: String) {
                text.lines().forEach { handleLine(it.trim()) }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connected.value = false
                if (shouldBeConnected) {
                    reconnect()
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                if (shouldBeConnected) {
                    reconnect()
                }
            }
        })
    }

    private fun reconnect() {
        scope.launch {
            kotlinx.coroutines.delay(5000)
            currentChannel?.let { 
                if (shouldBeConnected) connect(it) 
            }
        }
    }

    private fun handleLine(line: String) {
        if (line.isBlank()) return

        // Keep-alive
        if (line.startsWith("PING")) {
            socket?.send("PONG :tmi.twitch.tv")
            return
        }

        // Extract the channel's Twitch user ID from ROOMSTATE
        if (line.contains("ROOMSTATE")) {
            if (line.startsWith("@")) {
                val spaceIdx = line.indexOf(' ')
                if (spaceIdx > 0) {
                    line.substring(1, spaceIdx).split(";").forEach { tag ->
                        val eqIdx = tag.indexOf('=')
                        if (eqIdx >= 0 && tag.substring(0, eqIdx) == "room-id") {
                            val id = tag.substring(eqIdx + 1)
                            if (id.toLongOrNull() != null) _roomId.value = id
                        }
                    }
                }
            }
            return
        }

        if (!line.contains("PRIVMSG")) return

        val msg = parseTwitchIrcLine(line)
        if (msg != null) {
            _messages.value = (_messages.value + msg).takeLast(MAX_CHAT_MESSAGES)
            scope.launch { _newMessages.emit(msg) }
            msg.timestamp?.let { ts ->
                if (lastReceivedTimestamp == null || ts > lastReceivedTimestamp!!) {
                    lastReceivedTimestamp = ts
                }
            }
        }
    }

    private fun parseTwitchIrcLine(line: String): ChatMessage? {
        try {
            var rest = line
            var color: String? = null
            var displayName: String? = null
            var emotesTag: String? = null
            var badgesStr: String? = null
            var msgId: String? = null
            var serverTimestamp: Long? = null

            // Strip IRCv3 tags: @key=value;key=value ... <space> rest-of-line
            if (rest.startsWith("@")) {
                val spaceIdx = rest.indexOf(' ')
                if (spaceIdx < 0) return null
                val tagsStr = rest.substring(1, spaceIdx)
                rest = rest.substring(spaceIdx + 1).trimStart()

                tagsStr.split(";").forEach { tag ->
                    val eqIdx = tag.indexOf('=')
                    if (eqIdx < 0) return@forEach
                    val key = tag.substring(0, eqIdx)
                    val value = tag.substring(eqIdx + 1)
                    when (key) {
                        "color"           -> if (value.isNotEmpty()) color = value
                        "display-name"    -> if (value.isNotEmpty()) displayName = value
                        "emotes"          -> if (value.isNotEmpty()) emotesTag = value
                        "badges"          -> if (value.isNotEmpty()) badgesStr = value
                        "id"              -> if (value.isNotEmpty()) msgId = value
                        "tmi-sent-ts"     -> serverTimestamp = value.toLongOrNull()
                    }
                }
            }

            // rest: :nick!user@host PRIVMSG #channel :message text
            val prefix   = rest.removePrefix(":").substringBefore("!")
            val login    = prefix.ifEmpty { null }
            val username = displayName ?: login ?: return null

            // Message starts after the second ':'
            val msgIdx = rest.indexOf(':', 1)
            if (msgIdx < 0) return null
            val message = rest.substring(msgIdx + 1)
            if (message.isBlank()) return null

            val badges = badgesStr?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

            return ChatMessage(
                id         = msgId ?: java.util.UUID.randomUUID().toString(),
                username   = username,
                login      = login,
                message    = message,
                color      = color,
                emotesTag  = emotesTag,
                badgeTags  = badges,
                timestamp  = serverTimestamp,
                platform   = "twitch"
            )
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun fetchRecentMessages(channel: String): List<ChatMessage> = withContext(Dispatchers.IO) {
        var url = "https://recent-messages.robotty.de/api/v2/recent-messages/$channel?limit=200&data=json"
        lastReceivedTimestamp?.let { ts ->
            url += "&after=$ts"
        }
        val request = Request.Builder().url(url).build()
        try {
            val response = http.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Recent messages API 請求失敗: ${response.code}")
                return@withContext emptyList()
            }
            val jsonStr = response.body?.string() ?: ""
            if (jsonStr.isBlank()) return@withContext emptyList()

            val json = JSONObject(jsonStr)
            val messagesArray = json.optJSONArray("messages") ?: return@withContext emptyList()

            val recentList = mutableListOf<ChatMessage>()
            var maxTimestamp: Long? = lastReceivedTimestamp
            for (i in 0 until messagesArray.length()) {
                val rawMessage = messagesArray.optString(i)
                if (rawMessage.isBlank()) continue

                val msg = parseTwitchIrcLine(rawMessage)
                if (msg != null) {
                    recentList.add(msg)
                    msg.timestamp?.let { ts ->
                        if (maxTimestamp == null || ts > maxTimestamp!!) {
                            maxTimestamp = ts
                        }
                    }
                }
            }
            if (maxTimestamp != null && maxTimestamp != lastReceivedTimestamp) {
                lastReceivedTimestamp = maxTimestamp
            }
            recentList
        } catch (e: Exception) {
            Log.e(TAG, "獲取最近訊息失敗", e)
            emptyList()
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
        lastReceivedTimestamp = null
        currentChannel = null
    }

    fun disconnect() {
        shouldBeConnected = false
        disconnectInternal()
    }

    private fun disconnectInternal() {
        socket?.close(1000, null)
        socket = null
        _connected.value = false
        _roomId.value = ""
    }
}
