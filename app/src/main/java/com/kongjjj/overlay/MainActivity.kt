package com.kongjjj.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.kongjjj.overlay.ui.theme.ChatOverlayTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                startOverlayService()
            } else {
                Toast.makeText(this, "需要懸浮窗權限", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            ChatOverlayTheme {
                var showSettings by remember { mutableStateOf(false) }
                var showLanguageDialog by remember { mutableStateOf(false) }
                val chatManager = remember { ChatManager.getInstance(applicationContext) }
                
                val twitchChannel by chatManager.twitchChannel.collectAsState()
                val youtubeChannelId by chatManager.youtubeChannelId.collectAsState()
                val fontSize by chatManager.chatFontSize.collectAsState()
                val lineSpacing by chatManager.chatLineSpacing.collectAsState()
                val emoteSize by chatManager.chatEmoteSize.collectAsState()
                val usernameSize by chatManager.chatUsernameSize.collectAsState()
                val animated by chatManager.animatedEmotes.collectAsState()
                
                val backgroundColor by chatManager.backgroundColor.collectAsState()
                val appLanguage by chatManager.appLanguage.collectAsState()
                val ttsEnabled by chatManager.ttsEnabled.collectAsState()
                val ttsLanguage by chatManager.ttsLanguage.collectAsState()
                val ttsIgnoreSender by chatManager.ttsIgnoreSender.collectAsState()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Chat Overlay",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        Button(
                            onClick = { checkPermissionAndStart() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getLabel("Open Floating Chat", appLanguage))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { showSettings = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getLabel("Settings", appLanguage))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { showLanguageDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getLabel("App Language", appLanguage))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { finishAffinity() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(getLabel("Exit App", appLanguage))
                        }
                    }
                    
                    if (showLanguageDialog) {
                        LanguageSelectionDialog(
                            currentLanguage = appLanguage,
                            onLanguageSelected = { code ->
                                chatManager.saveAppLanguage(code, this@MainActivity)
                                showLanguageDialog = false
                            },
                            onDismiss = { showLanguageDialog = false },
                            appLanguage = appLanguage
                        )
                    }

                    if (showSettings) {
                        SettingsDialog(
                            twitchChannel = twitchChannel,
                            youtubeChannelId = youtubeChannelId,
                            chatFontSize = fontSize,
                            chatLineSpacing = lineSpacing,
                            chatEmoteSize = emoteSize,
                            chatUsernameSize = usernameSize,
                            animatedEmotes = animated,
                            enable7tv = chatManager.enable7tv.collectAsState().value,
                            enableBttv = chatManager.enableBttv.collectAsState().value,
                            enableFfz = chatManager.enableFfz.collectAsState().value,
                            backgroundColor = backgroundColor,
                            appLanguage = appLanguage,
                            ttsEnabled = ttsEnabled,
                            ttsLanguage = ttsLanguage,
                            ttsIgnoreSender = ttsIgnoreSender,
                            onSaveChannel = { chatManager.saveTwitchChannel(it, this@MainActivity) },
                            onSaveYoutubeChannelId = { chatManager.saveYoutubeChannelId(it, this@MainActivity) },
                            onFontSizeChange = { chatManager.saveFontSize(it, this@MainActivity) },
                            onLineSpacingChange = { chatManager.saveLineSpacing(it, this@MainActivity) },
                            onEmoteSizeChange = { chatManager.saveEmoteSize(it, this@MainActivity) },
                            onUsernameSizeChange = { chatManager.saveUsernameSize(it, this@MainActivity) },
                            onAnimatedEmotesChange = { chatManager.saveAnimatedEmotes(it, this@MainActivity) },
                            onEnable7tvChange = { chatManager.saveEnable7tv(it, this@MainActivity) },
                            onEnableBttvChange = { chatManager.saveEnableBttv(it, this@MainActivity) },
                            onEnableFfzChange = { chatManager.saveEnableFfz(it, this@MainActivity) },
                            onBackgroundColorChange = { chatManager.saveBackgroundColor(it, this@MainActivity) },
                            onTtsEnabledChange = { chatManager.saveTtsEnabled(it, this@MainActivity) },
                            onTtsLanguageChange = { chatManager.saveTtsLanguage(it, this@MainActivity) },
                            onTtsIgnoreSenderChange = { chatManager.saveTtsIgnoreSender(it, this@MainActivity) },
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            }
        }

    }

    private fun checkPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
                return
            }
        }
        startOverlayService()
    }

    private fun startOverlayService() {
        val intent = Intent(this, FloatingViewService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        moveTaskToBack(true)
    }

    // Helper for main screen labels
    private fun getLabel(key: String, lang: String): String {
        val labels = mapOf(
            "Open Floating Chat" to mapOf("zh-TW" to "開啟懸浮聊天室", "en" to "Open Floating Chat", "ja" to "フローティングチャットを開く"),
            "Settings" to mapOf("zh-TW" to "設定", "en" to "Settings", "ja" to "設定"),
            "App Language" to mapOf("zh-TW" to "程式語言", "en" to "App Language", "ja" to "アプリの言語"),
            "Exit App" to mapOf("zh-TW" to "關閉程式", "en" to "Close App", "ja" to "アプリを終了"),
            "Close" to mapOf("zh-TW" to "關閉", "en" to "Close", "ja" to "閉じる")
        )
        return labels[key]?.get(lang) ?: key
    }
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    appLanguage: String
) {
    val languages = listOf("zh-TW" to "繁體中文", "en" to "English", "ja" to "日本語")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getLabel("App Language", appLanguage)) },
        text = {
            Column {
                languages.forEach { (code, name) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(name, style = MaterialTheme.typography.bodyLarge)
                        if (code == currentLanguage) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(getLabel("Close", appLanguage))
            }
        }
    )
}
