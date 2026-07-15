package com.kongjjj.overlay

import android.util.Log
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class EmoteRepository {

    // Single shared client with a browser-like UA so APIs don't reject us
    private val http = OkHttpClient.Builder()
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
                    .header("Accept", "application/json")
                    .build()
            )
        }
        .build()

    private val _thirdPartyEmotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val thirdPartyEmotes: StateFlow<Map<String, String>> = _thirdPartyEmotes

    private val _twitchBadges = MutableStateFlow<Map<String, String>>(emptyMap())
    val twitchBadges: StateFlow<Map<String, String>> = _twitchBadges

    private val _loadReport = MutableStateFlow("loading…")
    val loadReport: StateFlow<String> = _loadReport

    /**
     * Loads emote providers and global badges sequentially on the IO dispatcher.
     * Clears existing third-party emotes first so toggling a provider off takes effect.
     * loadReport is updated with per-source counts, "off", or exception class names.
     */
    suspend fun loadAll(
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ) = withContext(Dispatchers.IO) {
        _thirdPartyEmotes.value = emptyMap()

        fun loadProvider(enabled: Boolean, fetch: () -> Map<String, String>): String {
            if (!enabled) return "off"
            return runCatching { fetch() }
                .onSuccess { map -> _thirdPartyEmotes.update { current -> current + map } }
                .fold({ it.size.toString() }, { it.javaClass.simpleName })
        }

        val s7tv  = loadProvider(enable7tv,  ::fetch7TV)
        val sBttv = loadProvider(enableBttv, ::fetchBTTV)
        val sFfz  = loadProvider(enableFfz,  ::fetchFFZ)

        val sBdg = runCatching { fetchTwitchBadges() }
            .onSuccess { badges -> _twitchBadges.value = badges }
            .fold({ it.size.toString() }, { it.javaClass.simpleName })

        _loadReport.value = "7tv:$s7tv bttv:$sBttv ffz:$sFfz bdg:$sBdg"
    }

    /**
     * Fetches channel-specific emotes for a joined channel and merges them into
     * the existing third-party emote map (does NOT clear globals first).
     * Requires the Twitch numeric room-id (from ROOMSTATE) and the channel login name.
     */
    suspend fun loadChannelEmotes(
        channelId: String,
        channelName: String,
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ) = withContext(Dispatchers.IO) {
        fun mergeProvider(enabled: Boolean, fetch: () -> Map<String, String>): String {
            if (!enabled) return "off"
            return runCatching { fetch() }
                .onSuccess { map -> _thirdPartyEmotes.update { current -> current + map } }
                .fold({ it.size.toString() }, { it.javaClass.simpleName })
        }

        val s7tv  = mergeProvider(enable7tv)  { fetch7TVChannel(channelId) }
        val sBttv = mergeProvider(enableBttv) { fetchBTTVChannel(channelId) }
        val sFfz  = mergeProvider(enableFfz)  { fetchFFZChannel(channelName) }

        val prev = _loadReport.value.substringBefore(" | ch:")
        _loadReport.value = "$prev | ch: 7tv:$s7tv bttv:$sBttv ffz:$sFfz"
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun get(url: String): String? = try {
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) resp.body?.string() else null
        }
    } catch (_: Exception) { null }

    // ── 7TV global emotes ─────────────────────────────────────────────────────
    private fun fetch7TV(): Map<String, String> {
        val body = get("https://7tv.io/v3/emote-sets/global") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val arr  = root.getAsJsonArray("emotes") ?: return emptyMap()
        return parse7tvEmoteArray(arr)
    }

    // ── BetterTTV global emotes ───────────────────────────────────────────────
    private fun fetchBTTV(): Map<String, String> {
        val body = get("https://api.betterttv.net/3/cached/emotes/global") ?: return emptyMap()
        val arr  = JsonParser.parseString(body).takeIf { it.isJsonArray }?.asJsonArray
            ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        arr.forEach { el ->
            runCatching {
                val obj  = el.asJsonObject
                val id   = obj.get("id").asString
                val code = obj.get("code").asString
                map[code] = "https://cdn.betterttv.net/emote/$id/1x"
            }
        }
        return map
    }

    // ── FrankerFaceZ global emotes ────────────────────────────────────────────
    private fun fetchFFZ(): Map<String, String> {
        val body = get("https://api.frankerfacez.com/v1/set/global") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val sets = root.getAsJsonObject("sets") ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        sets.entrySet().forEach { (_, setEl) ->
            runCatching {
                val emoticons = setEl.asJsonObject.getAsJsonArray("emoticons") ?: return@runCatching
                emoticons.forEach { emEl ->
                    runCatching {
                        val emObj = emEl.asJsonObject
                        val name  = emObj.get("name").asString
                        val url1  = emObj.getAsJsonObject("urls").get("1")?.asString
                            ?: return@runCatching
                        map[name] = "https:$url1"
                    }
                }
            }
        }
        return map
    }

    // ── 7TV channel emotes ────────────────────────────────────────────────────
    private fun fetch7TVChannel(channelId: String): Map<String, String> {
        val body = get("https://7tv.io/v3/users/twitch/$channelId") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val arr  = root.getAsJsonObject("emote_set")?.getAsJsonArray("emotes") ?: return emptyMap()
        return parse7tvEmoteArray(arr)
    }

    private fun parse7tvEmoteArray(arr: JsonArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        arr.forEach { el ->
            runCatching {
                val obj     = el.asJsonObject
                val name    = obj.get("name").asString
                val data    = obj.getAsJsonObject("data")
                val hostObj = data.getAsJsonObject("host")
                val hostUrl = hostObj.get("url").asString
                val files   = hostObj.getAsJsonArray("files")
                val hasGif  = files?.any { it.asJsonObject.get("name")?.asString == "1x.gif" } == true
                val ext     = if (hasGif) "1x.gif" else "1x.webp"
                map[name]   = "https:$hostUrl/$ext"
            }
        }
        return map
    }

    // ── BTTV channel emotes ───────────────────────────────────────────────────
    private fun fetchBTTVChannel(channelId: String): Map<String, String> {
        val body = get("https://api.betterttv.net/3/cached/users/twitch/$channelId") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        listOf("channelEmotes", "sharedEmotes").forEach { key ->
            root.getAsJsonArray(key)?.forEach { el ->
                runCatching {
                    val obj  = el.asJsonObject
                    val id   = obj.get("id").asString
                    val code = obj.get("code").asString
                    map[code] = "https://cdn.betterttv.net/emote/$id/1x"
                }
            }
        }
        return map
    }

    // ── FFZ channel emotes ────────────────────────────────────────────────────
    private fun fetchFFZChannel(channelName: String): Map<String, String> {
        val body = get("https://api.frankerfacez.com/v1/room/$channelName") ?: return emptyMap()
        val root = JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            ?: return emptyMap()
        val sets = root.getAsJsonObject("sets") ?: return emptyMap()
        val map  = mutableMapOf<String, String>()
        sets.entrySet().forEach { (_, setEl) ->
            runCatching {
                val emoticons = setEl.asJsonObject.getAsJsonArray("emoticons") ?: return@runCatching
                emoticons.forEach { emEl ->
                    runCatching {
                        val emObj = emEl.asJsonObject
                        val name  = emObj.get("name").asString
                        val url1  = emObj.getAsJsonObject("urls").get("1")?.asString
                            ?: return@runCatching
                        map[name] = "https:$url1"
                    }
                }
            }
        }
        return map
    }

    // ── Twitch global badges ──────────────────────────────────────────────────
    private fun fetchTwitchBadges(): Map<String, String> {
        val body = get("https://badges.twitch.tv/v1/badges/global/display?language=en")
        if (body != null) {
            runCatching {
                val root     = JsonParser.parseString(body).asJsonObject
                val sets     = root.getAsJsonObject("badge_sets")
                val map      = mutableMapOf<String, String>()
                sets.entrySet().forEach { (setName, setEl) ->
                    setEl.asJsonObject.getAsJsonObject("versions")
                        .entrySet().forEach { (version, verEl) ->
                            val url = verEl.asJsonObject.get("image_url_1x")?.asString
                            if (url != null) map["$setName/$version"] = url
                        }
                }
                if (map.isNotEmpty()) return map
            }
        }
        // Fallback
        fun url(uuid: String) = "https://static-cdn.jtvnw.net/badges/v1/$uuid/1"
        return mapOf(
            "admin/1"       to url("9ef7e029-4cdf-4d4d-a0d5-e2b3fb2583fe"),
            "broadcaster/1" to url("5527c58c-fb7d-422d-b71b-f309dcb85cc1"),
            "global_mod/1"  to url("9384c9a7-f2f2-4f74-b6e5-7f78898a43fc"),
            "moderator/1"   to url("3267646d-33f0-4b17-b3df-f923a41db1d0"),
            "partner/1"     to url("d12a2e27-16f6-41d0-ab77-b780518f00a3"),
            "premium/1"     to url("bbbe0db0-a598-423e-86d0-f9fb98ca1933"),
            "staff/1"       to url("d97c37be-57b3-4f3d-b8db-6dcb904f84c8"),
            "subscriber/0"  to url("5d9f2208-5dd8-11e7-8513-2ff4adfae661"),
            "turbo/1"       to url("bd444ec6-8f34-4bf9-91f4-af1e3428d80f"),
            "vip/1"         to url("b817aba4-fad8-49e2-b88a-7cc744dfa6ec"),
        )
    }
}

// ── Top-level parsing function (pure, callable from composables) ──────────────

fun parseMessageSegments(
    message:          String,
    emotesTag:        String?,
    thirdPartyEmotes: Map<String, String>,
    youtubeEmotes:    Map<String, String> = emptyMap()
): List<MessageSegment> {
    
    val allEmotes = thirdPartyEmotes + youtubeEmotes

    val twitchRanges = mutableListOf<Triple<Int, Int, String>>()
    emotesTag?.split("/")?.forEach { entry ->
        if (entry.isBlank()) return@forEach
        val colonIdx = entry.indexOf(':')
        if (colonIdx < 0) return@forEach
        val emoteId = entry.substring(0, colonIdx)
        val url = "https://static-cdn.jtvnw.net/emoticons/v2/$emoteId/default/dark/1.0"
        entry.substring(colonIdx + 1).split(",").forEach { range ->
            val dashIdx = range.indexOf('-')
            if (dashIdx < 0) return@forEach
            val start = range.substring(0, dashIdx).toIntOrNull() ?: return@forEach
            val end   = range.substring(dashIdx + 1).toIntOrNull() ?: return@forEach
            if (start <= end && end < message.length) twitchRanges.add(Triple(start, end, url))
        }
    }
    twitchRanges.sortBy { it.first }

    val stage1 = mutableListOf<MessageSegment>()
    var cursor = 0
    for ((start, end, url) in twitchRanges) {
        if (start > cursor) {
            val text = message.substring(cursor, start)
            if (text.isNotEmpty()) stage1.add(MessageSegment.TextPart(text))
        }
        val name = message.substring(start, end + 1)
        stage1.add(MessageSegment.EmotePart(name, url))
        cursor = end + 1
    }
    if (cursor < message.length) {
        val tail = message.substring(cursor)
        if (tail.isNotEmpty()) stage1.add(MessageSegment.TextPart(tail))
    }

    val stage2 = stage1.flatMap { seg ->
        if (seg is MessageSegment.TextPart) scanThirdPartyWords(seg.text, allEmotes)
        else listOf(seg)
    }

    return stage2.flatMap { seg ->
        if (seg is MessageSegment.TextPart) scanForLinks(seg.text)
        else listOf(seg)
    }
}

private fun scanThirdPartyWords(text: String, emotes: Map<String, String>): List<MessageSegment> {
    if (emotes.isEmpty()) return listOf(MessageSegment.TextPart(text))
    val escapedKeys = emotes.keys.filter { it.length >= 2 }.map { Regex.escape(it) }.sortedByDescending { it.length }
    if (escapedKeys.isEmpty()) return listOf(MessageSegment.TextPart(text))
    
    // Wrap the pattern with word boundaries or space check to ensure standalone words.
    // Use negative lookbehind/lookahead for non-whitespace to cover start/end of string and actual spaces.
    val pattern = "(?<!\\S)(${escapedKeys.joinToString("|")})(?!\\S)".toRegex()
    val result = mutableListOf<MessageSegment>()
    var cursor = 0

    pattern.findAll(text).forEach { match ->
        // The regex includes lookbehind/lookahead but the group 1 is just the emote name
        val matchRange = match.groups[1]?.range ?: match.range
        
        if (matchRange.first > cursor) {
            result.add(MessageSegment.TextPart(text.substring(cursor, matchRange.first)))
        }
        val word = match.groups[1]?.value ?: match.value
        val url = emotes[word]!!
        result.add(MessageSegment.EmotePart(word, url))
        cursor = matchRange.last + 1
    }

    if (cursor < text.length) {
        result.add(MessageSegment.TextPart(text.substring(cursor)))
    }
    return result
}

private val urlRegex = Regex(
    "((?:https?://|www\\.)[\\w\\d.\\-?&#=+%!:/~]+|(?:^|[\\s])(?:[\\w\\d\\-]+\\.)+(?:com|tv|net|org|io|me|gov|edu|tw|hk|jp|live)(?:/[\\w\\d.\\-?&#=+%!:/~]*)?)",
    RegexOption.IGNORE_CASE
)

private fun scanForLinks(text: String): List<MessageSegment> {
    val result = mutableListOf<MessageSegment>()
    var cursor = 0
    val matches = urlRegex.findAll(text)

    for (match in matches) {
        val matchedUrl = match.value.trim()
        val start = match.range.first + (match.value.length - match.value.trimStart().length)
        val end = start + matchedUrl.length

        if (start > cursor) {
            result.add(MessageSegment.TextPart(text.substring(cursor, start)))
        }

        val fullUrl = if (matchedUrl.startsWith("http", ignoreCase = true)) matchedUrl 
                     else "https://$matchedUrl"
        result.add(MessageSegment.LinkPart(matchedUrl, fullUrl))
        cursor = end
    }

    if (cursor < text.length) {
        result.add(MessageSegment.TextPart(text.substring(cursor)))
    }

    return result
}
