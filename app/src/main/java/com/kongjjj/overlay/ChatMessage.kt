package com.kongjjj.overlay

data class ChatMessage(
    val id: String,
    val username: String,
    val login: String? = null,
    val message: String,
    val color: String? = null,                  // hex like "#FF4500" or null
    val emotesTag: String? = null,              // raw IRC emotes tag e.g. "25:0-4,6-10/1902:12-17"
    val badgeTags: List<String> = emptyList(),  // e.g. ["broadcaster/1", "subscriber/0", "premium/1"] or full URLs
    val youtubeEmotes: Map<String, String> = emptyMap(), // shortcut -> url
    val timestamp: Long? = null,
    val platform: String = "twitch"
)
