package com.kongjjj.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.kongjjj.overlay.ui.theme.ChatOverlayTheme
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.seconds

class FloatingViewService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var chatManager: ChatManager

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = lifecycleRegistry

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry = savedStateRegistryController.savedStateRegistry

    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var initialWidth = 0
    private var initialHeight = 0
    private var savedWidth = 0
    private var savedHeight = 0
    private var isCollapsed = false
    private val uiVisible = mutableStateOf(value = true)
    private var hideJob: Job? = null
    private val serviceScope = MainScope()

    private fun startHideTimer() {
        hideJob?.cancel()
        hideJob = serviceScope.launch {
            delay(3.seconds)
            uiVisible.value = false
            updateViewsVisibility()
        }
    }

    private fun resetHideTimer() {
        uiVisible.value = true
        updateViewsVisibility()
        startHideTimer()
    }

    private fun updateViewsVisibility() {
        if (!::floatingView.isInitialized) return
        val visible = uiVisible.value
        floatingView.findViewById<View>(R.id.top_controls_container)?.visibility = if (visible) View.VISIBLE else View.GONE
        floatingView.findViewById<View>(R.id.resize_handle)?.visibility = if (visible) View.VISIBLE else View.GONE
        floatingView.findViewById<View>(R.id.drag_handle)?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onCreate() {
        super.onCreate()
        // SavedStateRegistry must be restored while lifecycle is in INITIALIZED state
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        
        windowManager = getSystemService(WindowManager::class.java)
        chatManager = ChatManager.getInstance(applicationContext)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        inflateFloatingView()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW,
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Chat Overlay")
            .setContentText("Running...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun inflateFloatingView() {
        val themeContext = ContextThemeWrapper(this, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)
        // Use a dummy FrameLayout to allow LayoutInflater to resolve layout parameters
        val dummyParent = FrameLayout(themeContext)
        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.floating_view, dummyParent, false)

        floatingView.setViewTreeLifecycleOwner(this)
        floatingView.setViewTreeSavedStateRegistryOwner(this)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val metrics = resources.displayMetrics
        val widthPx = (320 * metrics.density).toInt()
        val heightPx = (180 * metrics.density).toInt()

        params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        windowManager.addView(floatingView, params)

        startHideTimer()

        val composeView = ComposeView(this).apply {
            setBackgroundColor(0)
            setContent {
                ChatOverlayTheme {
                    val twitchChannel by chatManager.twitchChannel.collectAsState()
                    val youtubeChannelId by chatManager.youtubeChannelId.collectAsState()
                    
                    val twitchMessages by chatManager.twitchClient.messages.collectAsState()
                    val youtubeMessages by chatManager.youtubeClient.messages.collectAsState()
                    
                    val chatMessages = remember(twitchChannel, youtubeChannelId, twitchMessages, youtubeMessages) {
                        (twitchMessages + youtubeMessages).sortedBy { it.timestamp ?: 0L }.takeLast(MAX_CHAT_MESSAGES)
                    }

                    val twitchConnected by chatManager.twitchClient.connected.collectAsState()
                    val youtubeConnected by chatManager.youtubeClient.connected.collectAsState()
                    val chatConnected = twitchConnected || youtubeConnected

                    val thirdPartyEmotes by chatManager.emoteRepository.thirdPartyEmotes.collectAsState()
                    val twitchBadges by chatManager.emoteRepository.twitchBadges.collectAsState()
                    
                    val fontSize by chatManager.chatFontSize.collectAsState()
                    val lineSpacing by chatManager.chatLineSpacing.collectAsState()
                    val emoteSize by chatManager.chatEmoteSize.collectAsState()
                    val usernameSize by chatManager.chatUsernameSize.collectAsState()
                    val animated by chatManager.animatedEmotes.collectAsState()
                    val showTimestamp by chatManager.showTimestamp.collectAsState()
                    val backgroundColor by chatManager.backgroundColor.collectAsState()
                    val isVisible by uiVisible
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (backgroundColor == "black") Color.Black else Color.Transparent)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val halfWidth = size.width / 2
                                    if (offset.x < halfWidth) {
                                        resetHideTimer()
                                    }
                                }
                            },
                    ) {
                        ChatScreen(
                            twitchChannel = twitchChannel,
                            youtubeChannelId = youtubeChannelId,
                            chatMessages = chatMessages,
                            chatConnected = chatConnected,
                            thirdPartyEmotes = thirdPartyEmotes,
                            twitchBadges = twitchBadges,
                            chatFontSize = fontSize,
                            chatLineSpacing = lineSpacing,
                            chatEmoteSize = emoteSize,
                            chatUsernameSize = usernameSize,
                            animatedEmotes = animated,
                            showTimestamp = showTimestamp,
                            showChrome = isVisible,
                        ) {
                            chatManager.connect()
                        }
                    }
                }
            }
        }

        val webView = floatingView.findViewById<View>(R.id.floating_webview)
        val parent = webView.parent as ViewGroup
        val index = parent.indexOfChild(webView)
        parent.removeView(webView)
        parent.addView(composeView, index, webView.layoutParams)

        setupViewLogic(metrics)
    }

    private fun setupViewLogic(metrics: DisplayMetrics) {
        val border = floatingView.findViewById<View>(R.id.overlay_border)
        val minimizedIcon = floatingView.findViewById<View>(R.id.minimized_icon)
        val mainContentContainer = floatingView.findViewById<View>(R.id.main_content_container)
        val resizeHandle = floatingView.findViewById<View>(R.id.resize_handle)

        floatingView.findViewById<View>(R.id.btn_restore).setOnClickListener {
            resetHideTimer()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            stopSelf()
        }

        floatingView.findViewById<View>(R.id.btn_collapse).setOnClickListener {
            resetHideTimer()
            if (!isCollapsed) {
                savedWidth = params.width
                savedHeight = params.height
                isCollapsed = true
                mainContentContainer.visibility = View.GONE
                minimizedIcon.visibility = View.VISIBLE
                params.width = (60 * metrics.density).toInt()
                params.height = (60 * metrics.density).toInt()
                windowManager.updateViewLayout(floatingView, params)
            }
        }

        minimizedIcon.setOnClickListener {
            if (isCollapsed) {
                isCollapsed = false
                minimizedIcon.visibility = View.GONE
                mainContentContainer.visibility = View.VISIBLE
                params.width = savedWidth
                params.height = savedHeight
                windowManager.updateViewLayout(floatingView, params)
            }
        }

        minimizedIcon.setOnTouchListener { v, event ->
            if (!isCollapsed) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moveX = event.rawX - initialTouchX
                    val moveY = event.rawY - initialTouchY
                    if (abs(moveX) < 10 && abs(moveY) < 10) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        val dragHandle = floatingView.findViewById<View>(R.id.drag_handle)
        dragHandle.setOnTouchListener { v, event ->
            if (isCollapsed) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resetHideTimer()
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    border.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    resetHideTimer()
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetHideTimer()
                    border.visibility = View.GONE
                    if (event.action == MotionEvent.ACTION_UP) {
                        val moveX = event.rawX - initialTouchX
                        val moveY = event.rawY - initialTouchY
                        if (abs(moveX) < 10 && abs(moveY) < 10) {
                            v.performClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }

        resizeHandle.setOnTouchListener { v, event ->
            if (isCollapsed) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resetHideTimer()
                    initialWidth = params.width
                    initialHeight = params.height
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    border.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    resetHideTimer()
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    params.width = max((100 * metrics.density).toInt(), initialWidth + deltaX)
                    params.height = max((100 * metrics.density).toInt(), initialHeight + deltaY)
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    resetHideTimer()
                    border.visibility = View.GONE
                    if (event.action == MotionEvent.ACTION_UP) {
                        val moveX = event.rawX - initialTouchX
                        val moveY = event.rawY - initialTouchY
                        if (abs(moveX) < 10 && abs(moveY) < 10) {
                            v.performClick()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "FloatingViewServiceChannel"
        private const val CHANNEL_NAME = "Floating View Service"
    }
}
