package com.kongjjj.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder

@Composable
fun ChatScreen(
    twitchChannel: String,
    youtubeChannelId: String,
    chatMessages: List<ChatMessage>,
    chatConnected: Boolean,
    thirdPartyEmotes: Map<String, String>,
    twitchBadges: Map<String, String>,
    chatFontSize: Float,
    chatLineSpacing: Float,
    chatEmoteSize: Float,
    chatUsernameSize: Float,
    animatedEmotes: Boolean,
    showTimestamp: Boolean = false,
    showChrome: Boolean = true,
    onConnect: () -> Unit
) {
    val listState = rememberLazyListState()
    var lastItemCount by remember { mutableIntStateOf(0) }
    var forceScrollToBottom by remember { mutableStateOf(false) }

    // Auto-connect when a channel is configured
    LaunchedEffect(twitchChannel, youtubeChannelId) {
        if (twitchChannel.isNotEmpty() || youtubeChannelId.isNotEmpty()) onConnect()
    }

    // Snap to bottom when connection is established/restored
    LaunchedEffect(chatConnected) {
        if (chatConnected) {
            forceScrollToBottom = true
        }
    }

    // Auto-scroll logic:
    // Trigger on ANY message update (even if size stays at 100)
    LaunchedEffect(chatMessages) {
        if (chatMessages.isNotEmpty()) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            
            // Check if user is currently near the bottom of the list
            val isAtBottom = if (visibleItems.isNotEmpty()) {
                val lastVisibleIndex = visibleItems.last().index
                val totalItems = layoutInfo.totalItemsCount
                // Threshold of 25 messages to be very forgiving
                lastVisibleIndex >= totalItems - 25 || (lastItemCount > 0 && lastVisibleIndex >= lastItemCount - 25)
            } else {
                // If it's the first time messages appear
                true
            }

            if ((isAtBottom || forceScrollToBottom) && !listState.isScrollInProgress) {
                // scrollToItem is more reliable than animateScrollToItem for rapid updates
                listState.scrollToItem(chatMessages.size - 1)
                forceScrollToBottom = false
            }
        }
        lastItemCount = chatMessages.size
    }

    val context = LocalContext.current
    val imageLoader: ImageLoader = remember(animatedEmotes) {
        if (animatedEmotes) {
            ImageLoader.Builder(context).components { add(GifDecoder.Factory()) }.build()
        } else {
            ImageLoader.Builder(context).build()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Status row ────────────────────────────────────────────────────────
        if (showChrome && (twitchChannel.isNotEmpty() || youtubeChannelId.isNotEmpty())) {
            Surface(tonalElevation = 2.dp, color = Color.Transparent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Added spacer to avoid overlap with the ic_back button (25dp)
                    Spacer(modifier = Modifier.width(28.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (twitchChannel.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (chatConnected) Color(0xFF9146FF) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "Twitch: #$twitchChannel",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (youtubeChannelId.isNotEmpty()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            color = if (chatConnected) Color(0xFFFF0000) else Color(0xFF9E9E9E),
                                            shape = CircleShape
                                        )
                                )
                                Text(
                                    text = "YouTube: $youtubeChannelId",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    if (!chatConnected) {
                        TextButton(onClick = onConnect) { Text("Reconnect") }
                    }

                    // Added spacer to avoid overlap with the ic_close button (25dp)
                    Spacer(modifier = Modifier.width(28.dp))
                }
            }
            HorizontalDivider()
        }

        // ── Messages / empty states ───────────────────────────────────────────
        if (twitchChannel.isEmpty() && youtubeChannelId.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap the StudioBridge title above to open Settings and enter your Twitch channel or YouTube Channel ID.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else if (chatMessages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (chatConnected) "Waiting for chat messages…" else "Not connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    ChatMessageRow(
                        message = msg,
                        thirdPartyEmotes = thirdPartyEmotes,
                        twitchBadges = twitchBadges,
                        fontSize = chatFontSize,
                        lineSpacing = chatLineSpacing,
                        emoteSize = chatEmoteSize,
                        usernameSize = chatUsernameSize,
                        showTimestamp = showTimestamp,
                        imageLoader = imageLoader
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    thirdPartyEmotes: Map<String, String>,
    twitchBadges: Map<String, String>,
    fontSize: Float,
    lineSpacing: Float,
    emoteSize: Float,
    usernameSize: Float,
    showTimestamp: Boolean,
    imageLoader: ImageLoader
) {
    val badgeSize = (fontSize * 1.1f).sp
    val emoteSizeSp = emoteSize.sp

    val defaultColor = MaterialTheme.colorScheme.primary
    val nameColor: Color = remember(message.color, defaultColor) {
        if (message.color != null) {
            try { Color(message.color.toColorInt()) }
            catch (_: Exception) { defaultColor }
        } else defaultColor
    }

    // Resolve badge URLs, falling back to version "0" for channel-specific sets
    val badgeUrls: List<String> = remember(message.id, twitchBadges.size) {
        message.badgeTags.map { tag ->
            when {
                tag.startsWith("http") -> tag
                tag == "yt-MODERATOR" -> "local:ic_youtubemod"
                tag == "yt-OWNER" -> "https://www.gstatic.com/youtube/img/live_chat/badges/owner_active.png"
                tag == "yt-VERIFIED" -> "https://www.gstatic.com/youtube/img/live_chat/badges/verified_active.png"
                else -> twitchBadges[tag] ?: twitchBadges["${tag.substringBefore('/')}/0"] ?: ""
            }
        }.filter { it.isNotEmpty() }
    }

    val segments: List<MessageSegment> = remember(message.id, thirdPartyEmotes.size) {
        parseMessageSegments(message.message, message.emotesTag, thirdPartyEmotes, message.youtubeEmotes)
    }

    val timestampText = remember(message.timestamp, showTimestamp) {
        if (showTimestamp && message.timestamp != null) {
            val date = java.util.Date(message.timestamp)
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            sdf.format(date) + " "
        } else ""
    }

    val inlineContent: Map<String, InlineTextContent> = remember(
        message.id, thirdPartyEmotes.size, twitchBadges.size, imageLoader, fontSize, emoteSize
    ) {
        buildMap {
            put("platform_icon", InlineTextContent(
                Placeholder(badgeSize, badgeSize, PlaceholderVerticalAlign.TextCenter)
            ) {
                val iconRes = if (message.platform == "youtube") R.drawable.ic_youtube else R.drawable.ic_twitch
                AsyncImage(model = iconRes, contentDescription = message.platform,
                    imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
            })
            badgeUrls.forEach { url ->
                put(url, InlineTextContent(
                    Placeholder(badgeSize, badgeSize, PlaceholderVerticalAlign.TextCenter)
                ) {
                    val model: Any = if (url == "local:ic_youtubemod") R.drawable.ic_youtubemod else url
                    AsyncImage(model = model, contentDescription = null,
                        imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                })
            }
            segments.filterIsInstance<MessageSegment.EmotePart>()
                .distinctBy { it.url }
                .forEach { emote ->
                    put(emote.url, InlineTextContent(
                        Placeholder(emoteSizeSp, emoteSizeSp, PlaceholderVerticalAlign.TextCenter)
                    ) {
                        AsyncImage(model = emote.url, contentDescription = emote.name,
                            imageLoader = imageLoader, modifier = Modifier.fillMaxSize())
                    })
                }
        }
    }

    val annotatedText = remember(message.id, thirdPartyEmotes.size, twitchBadges.size, nameColor, usernameSize, timestampText) {
        buildAnnotatedString {
            if (timestampText.isNotEmpty()) {
                withStyle(SpanStyle(color = Color.LightGray.copy(alpha = 0.8f), fontSize = (fontSize * 0.8f).sp)) {
                    append(timestampText)
                }
            }

            if (message.platform == "youtube" || message.platform == "twitch") {
                appendInlineContent("platform_icon", "[${message.platform}]")
                append(' ')
            }

            // Twitch: badges BEFORE name
            if (message.platform == "twitch") {
                badgeUrls.forEachIndexed { i, url ->
                    appendInlineContent(url, "[badge]")
                    if (i < badgeUrls.lastIndex) append('\u2009') else append(' ')
                }
            }

            withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.SemiBold, fontSize = usernameSize.sp)) {
                append(message.username)
                if (message.login != null && !message.login.equals(message.username, ignoreCase = true)) {
                    append(" (${message.login})")
                }
            }

            // YouTube: badges AFTER name
            if (message.platform == "youtube") {
                badgeUrls.forEachIndexed { i, url ->
                    if (i == 0) append(' ')
                    appendInlineContent(url, "[badge]")
                    if (i < badgeUrls.lastIndex) append('\u2009')
                }
            }

            append(": ")
            segments.forEach { seg ->
                when (seg) {
                    is MessageSegment.TextPart  -> append(seg.text)
                    is MessageSegment.EmotePart -> appendInlineContent(seg.url, "[${seg.name}]")
                    is MessageSegment.LinkPart  -> append(seg.text)
                }
            }
        }
    }

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        style = MaterialTheme.typography.bodySmall.copy(
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize.sp,
            lineHeight = (fontSize + lineSpacing).sp,
            shadow = Shadow(
                color = Color.Black,
                offset = Offset(2f, 2f),
                blurRadius = 4f
            )
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
    )
}
