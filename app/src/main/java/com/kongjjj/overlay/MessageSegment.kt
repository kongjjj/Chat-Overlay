package com.kongjjj.overlay

sealed class MessageSegment {
    data class TextPart(val text: String) : MessageSegment()
    data class EmotePart(val name: String, val url: String) : MessageSegment()
    data class LinkPart(val text: String, val url: String) : MessageSegment()
}
