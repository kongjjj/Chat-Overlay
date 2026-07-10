package com.kongjjj.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsDialog(
    twitchChannel: String,
    youtubeChannelId: String,
    chatFontSize: Float,
    chatLineSpacing: Float,
    chatEmoteSize: Float,
    chatUsernameSize: Float,
    animatedEmotes: Boolean,
    enable7tv: Boolean,
    enableBttv: Boolean,
    enableFfz: Boolean,
    backgroundColor: String,
    appLanguage: String,
    ttsEnabled: Boolean,
    ttsLanguage: String,
    ttsIgnoreSender: Boolean,
    onSaveChannel: (String) -> Unit,
    onSaveYoutubeChannelId: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onEmoteSizeChange: (Float) -> Unit,
    onUsernameSizeChange: (Float) -> Unit,
    onAnimatedEmotesChange: (Boolean) -> Unit,
    onEnable7tvChange: (Boolean) -> Unit,
    onEnableBttvChange: (Boolean) -> Unit,
    onEnableFfzChange: (Boolean) -> Unit,
    onBackgroundColorChange: (String) -> Unit,
    onTtsEnabledChange: (Boolean) -> Unit,
    onTtsLanguageChange: (String) -> Unit,
    onTtsIgnoreSenderChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getLabel("Settings", appLanguage)) },
        text = {
            SettingsContent(
                twitchChannel = twitchChannel,
                youtubeChannelId = youtubeChannelId,
                chatFontSize = chatFontSize,
                chatLineSpacing = chatLineSpacing,
                chatEmoteSize = chatEmoteSize,
                chatUsernameSize = chatUsernameSize,
                animatedEmotes = animatedEmotes,
                enable7tv = enable7tv,
                enableBttv = enableBttv,
                enableFfz = enableFfz,
                backgroundColor = backgroundColor,
                appLanguage = appLanguage,
                ttsEnabled = ttsEnabled,
                ttsLanguage = ttsLanguage,
                ttsIgnoreSender = ttsIgnoreSender,
                onSaveChannel = onSaveChannel,
                onSaveYoutubeChannelId = onSaveYoutubeChannelId,
                onFontSizeChange = onFontSizeChange,
                onLineSpacingChange = onLineSpacingChange,
                onEmoteSizeChange = onEmoteSizeChange,
                onUsernameSizeChange = onUsernameSizeChange,
                onAnimatedEmotesChange = onAnimatedEmotesChange,
                onEnable7tvChange = onEnable7tvChange,
                onEnableBttvChange = onEnableBttvChange,
                onEnableFfzChange = onEnableFfzChange,
                onBackgroundColorChange = onBackgroundColorChange,
                onTtsEnabledChange = onTtsEnabledChange,
                onTtsLanguageChange = onTtsLanguageChange,
                onTtsIgnoreSenderChange = onTtsIgnoreSenderChange,
                onDismiss = onDismiss
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(getLabel("Close", appLanguage)) }
        }
    )
}

@Composable
fun SettingsContent(
    twitchChannel: String,
    youtubeChannelId: String,
    chatFontSize: Float,
    chatLineSpacing: Float,
    chatEmoteSize: Float,
    chatUsernameSize: Float,
    animatedEmotes: Boolean,
    enable7tv: Boolean,
    enableBttv: Boolean,
    enableFfz: Boolean,
    backgroundColor: String,
    appLanguage: String,
    ttsEnabled: Boolean,
    ttsLanguage: String,
    ttsIgnoreSender: Boolean,
    onSaveChannel: (String) -> Unit,
    onSaveYoutubeChannelId: (String) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onEmoteSizeChange: (Float) -> Unit,
    onUsernameSizeChange: (Float) -> Unit,
    onAnimatedEmotesChange: (Boolean) -> Unit,
    onEnable7tvChange: (Boolean) -> Unit,
    onEnableBttvChange: (Boolean) -> Unit,
    onEnableFfzChange: (Boolean) -> Unit,
    onBackgroundColorChange: (String) -> Unit,
    onTtsEnabledChange: (Boolean) -> Unit,
    onTtsLanguageChange: (String) -> Unit,
    onTtsIgnoreSenderChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var channelInput by remember(twitchChannel) { mutableStateOf(twitchChannel) }
    var youtubeInput by remember(youtubeChannelId) { mutableStateOf(youtubeChannelId) }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Twitch channel
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(getLabel("Twitch Channel", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    placeholder = { Text(getLabel("Channel Name", appLanguage)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = channelInput.trim().lowercase()
                        onSaveChannel(trimmed)
                    })
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = channelInput.trim().lowercase()
                        onSaveChannel(trimmed)
                    }
                ) { Text(getLabel("Save", appLanguage)) }
            }
        }

        // YouTube Channel ID
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(getLabel("YouTube Channel ID", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = youtubeInput,
                    onValueChange = { youtubeInput = it },
                    placeholder = { Text("Channel ID (UC...)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val trimmed = youtubeInput.trim()
                        onSaveYoutubeChannelId(trimmed)
                    })
                )
                FilledTonalButton(
                    onClick = {
                        val trimmed = youtubeInput.trim()
                        onSaveYoutubeChannelId(trimmed)
                    }
                ) { Text(getLabel("Save", appLanguage)) }
            }
        }

        HorizontalDivider()

        // Background Color
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(getLabel("Background Color", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = backgroundColor == "transparent", onClick = { onBackgroundColorChange("transparent") })
                    Text(getLabel("Transparent", appLanguage), modifier = Modifier.clickable { onBackgroundColorChange("transparent") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = backgroundColor == "black", onClick = { onBackgroundColorChange("black") })
                    Text(getLabel("Black", appLanguage), modifier = Modifier.clickable { onBackgroundColorChange("black") })
                }
            }
        }

        HorizontalDivider()

        // TTS Settings
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(getLabel("TTS", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(getLabel("Enable TTS", appLanguage), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = ttsEnabled, onCheckedChange = onTtsEnabledChange)
            }

            if (ttsEnabled) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(getLabel("TTS Language", appLanguage), style = MaterialTheme.typography.bodyMedium)
                    
                    var expanded by remember { mutableStateOf(false) }
                    val languages = listOf(
                        "zh-HK" to "廣東話 (香港)",
                        "zh-TW" to "國語 (台灣)",
                        "zh-CN" to "國語 (中國)",
                        "en-US" to "English (US)",
                        "ja-JP" to "日本語"
                    )
                    val currentLangName = languages.find { it.first == ttsLanguage }?.second ?: ttsLanguage

                    Box {
                        Row(modifier = Modifier.clickable { expanded = true }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(currentLangName, style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            languages.forEach { (code, name) ->
                                DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        onTtsLanguageChange(code)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(getLabel("Ignore Sender", appLanguage), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = ttsIgnoreSender, onCheckedChange = onTtsIgnoreSenderChange)
                }
            }
        }

        HorizontalDivider()

        // Font size
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(getLabel("Font Size", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${chatFontSize.roundToInt()} sp", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = chatFontSize, onValueChange = onFontSizeChange, valueRange = 10f..20f, steps = 9, modifier = Modifier.fillMaxWidth())
        }

        // Username size
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(getLabel("Username Size", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${chatUsernameSize.roundToInt()} sp", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = chatUsernameSize, onValueChange = onUsernameSizeChange, valueRange = 10f..20f, steps = 9, modifier = Modifier.fillMaxWidth())
        }

        // Line spacing
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(getLabel("Line Spacing", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${chatLineSpacing.roundToInt()} dp", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = chatLineSpacing, onValueChange = onLineSpacingChange, valueRange = 0f..12f, steps = 11, modifier = Modifier.fillMaxWidth())
        }

        // Emote size
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(getLabel("Emote Size", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${chatEmoteSize.roundToInt()} sp", style = MaterialTheme.typography.labelMedium)
            }
            Slider(value = chatEmoteSize, onValueChange = onEmoteSizeChange, valueRange = 16f..48f, steps = 15, modifier = Modifier.fillMaxWidth())
        }

        // Reset
        TextButton(
            onClick = {
                onFontSizeChange(DEFAULT_FONT_SIZE)
                onUsernameSizeChange(DEFAULT_USERNAME_SIZE)
                onLineSpacingChange(DEFAULT_LINE_SPACING)
                onEmoteSizeChange(DEFAULT_EMOTE_SIZE)
            },
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(getLabel("Reset to Defaults", appLanguage), style = MaterialTheme.typography.labelSmall)
        }

        HorizontalDivider()

        // Animated emotes
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(getLabel("Animated Emotes", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(getLabel("Show GIF as animated images", appLanguage), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }
            Switch(checked = animatedEmotes, onCheckedChange = onAnimatedEmotesChange)
        }

        HorizontalDivider()

        // Emote providers
        Text(getLabel("Emote Sources", appLanguage), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("7TV", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enable7tv, onCheckedChange = onEnable7tvChange)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("BetterTTV", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enableBttv, onCheckedChange = onEnableBttvChange)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("FrankerFaceZ", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = enableFfz, onCheckedChange = onEnableFfzChange)
        }
    }
}

fun getLabel(key: String, lang: String): String {
    val labels = mapOf(
        "Settings" to mapOf("zh-TW" to "設定", "en" to "Settings", "ja" to "設定"),
        "Close" to mapOf("zh-TW" to "關閉", "en" to "Close", "ja" to "閉じる"),
        "Twitch Channel" to mapOf("zh-TW" to "Twitch 頻道", "en" to "Twitch Channel", "ja" to "Twitchチャンネル"),
        "YouTube Channel ID" to mapOf("zh-TW" to "YouTube 頻道 ID", "en" to "YouTube Channel ID", "ja" to "YouTubeチャンネルID"),
        "Channel Name" to mapOf("zh-TW" to "頻道名稱", "en" to "Channel Name", "ja" to "チャンネル名"),
        "Save" to mapOf("zh-TW" to "儲存", "en" to "Save", "ja" to "保存"),
        "Background Color" to mapOf("zh-TW" to "背景顏色", "en" to "Background Color", "ja" to "背景色"),
        "Transparent" to mapOf("zh-TW" to "透明", "en" to "Transparent", "ja" to "透明"),
        "Black" to mapOf("zh-TW" to "黑色", "en" to "Black", "ja" to "黒"),
        "TTS" to mapOf("zh-TW" to "TTS 語音朗讀", "en" to "TTS", "ja" to "TTS"),
        "Enable TTS" to mapOf("zh-TW" to "開啟 TTS", "en" to "Enable TTS", "ja" to "TTSを有効にする"),
        "TTS Language" to mapOf("zh-TW" to "朗讀語系", "en" to "TTS Language", "ja" to "TTS言語"),
        "Ignore Sender" to mapOf("zh-TW" to "忽略使用者名稱", "en" to "Ignore Sender", "ja" to "送信者を無視"),
        "Font Size" to mapOf("zh-TW" to "字型大小", "en" to "Font Size", "ja" to "フォントサイズ"),
        "Username Size" to mapOf("zh-TW" to "使用者名稱大小", "en" to "Username Size", "ja" to "ユーザー名サイズ"),
        "Line Spacing" to mapOf("zh-TW" to "行距", "en" to "Line Spacing", "ja" to "行間"),
        "Emote Size" to mapOf("zh-TW" to "表情符號大小", "en" to "Emote Size", "ja" to "エモートサイズ"),
        "Reset to Defaults" to mapOf("zh-TW" to "重設為預設值", "en" to "Reset to Defaults", "ja" to "デフォルトに戻す"),
        "Animated Emotes" to mapOf("zh-TW" to "動態表情符號", "en" to "Animated Emotes", "ja" to "アニメーションエモート"),
        "Show GIF as animated images" to mapOf("zh-TW" to "將 GIF 顯示為動態圖片", "en" to "Show GIF as animated images", "ja" to "GIFをアニメーション画像として表示"),
        "Emote Sources" to mapOf("zh-TW" to "表情符號來源", "en" to "Emote Sources", "ja" to "エモートソース"),
        "App Language" to mapOf("zh-TW" to "App 語言", "en" to "App Language", "ja" to "アプリの言語")
    )
    return labels[key]?.get(lang) ?: key
}
