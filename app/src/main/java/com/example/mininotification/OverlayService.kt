package com.example.mininotification

import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.ImageSpan
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import java.util.*

// 定义通知数据类
data class NotificationData(
    val settings: SettingsUiState,
    val duration: Int,
    val title: String?,
    val text: String?,
    val icon: ByteArray?,
    val isOngoing: Boolean = false, // 添加是否为常驻通知的标识
    val id: Long = System.currentTimeMillis(), // 为每个通知分配唯一ID
    val packageName: String? = null, // 添加包名用于跳转到应用
    val contentIntent: android.app.PendingIntent? = null, // 添加PendingIntent用于跳转到具体页面
    val statusBarNotification: android.service.notification.StatusBarNotification? = null // 添加StatusBarNotification对象
) {
    // Auto-generated equals and hashcode need to be updated for ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as NotificationData
        if (settings != other.settings) return false
        if (duration != other.duration) return false
        if (title != other.title) return false
        if (text != other.text) return false
        if (isOngoing != other.isOngoing) return false
        if (id != other.id) return false
        if (packageName != other.packageName) return false
        if (contentIntent != other.contentIntent) return false
        if (statusBarNotification != other.statusBarNotification) return false
        if (icon != null) {
            if (other.icon == null) return false
            if (!icon.contentEquals(other.icon)) return false
        } else if (other.icon != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = settings.hashCode()
        result = 31 * result + duration
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        result = 31 * result + isOngoing.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + (packageName?.hashCode() ?: 0)
        result = 31 * result + (contentIntent?.hashCode() ?: 0)
        result = 31 * result + (statusBarNotification?.hashCode() ?: 0)
        return result
    }
}

class OverlayService : Service(), ViewModelStoreOwner, SavedStateRegistryOwner, LifecycleOwner {

    private lateinit var windowManager: WindowManager
    private var overlayView: LinearLayout? = null
    private var textView: TextView? = null
    private var iconView: ImageView? = null
    private var titleView: TextView? = null
    private var contentView: TextView? = null
    private val notificationQueue: Queue<NotificationData> = LinkedList()
    private val handler = Handler(Looper.getMainLooper())
    private var currentNotificationId: Long? = null
    private var removalRunnable: Runnable? = null
    private var marqueeAnimator: ObjectAnimator? = null
    private var startMarqueeRunnable: Runnable? = null
    // 保存当前的通知位置，用于比较是否需要更新位置
    private var currentPopupPosition: PopupPosition? = null
    // 保存当前的通知样式，用于比较是否需要更新样式
    private var currentPopupStyle: PopupStyle? = null
    // 保存当前的通知数据
    private var currentNotificationData: NotificationData? = null

    // --- Lifecycle / ViewModelStore / SavedState Boilerplate ---
    override val viewModelStore = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "OVERLAY_SERVICE")
            .setContentTitle("浮动通知服务运行中")
            .setContentText("正在监听并显示通知")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("OVERLAY_SERVICE", "浮动通知服务", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        if (intent != null) {
            val position = intent.getSerializableExtra("position") as? PopupPosition ?: PopupPosition.TOP
            val style = intent.getSerializableExtra("style") as? PopupStyle ?: PopupStyle.NARROW
            val backgroundColor = intent.getIntExtra("backgroundColor", 0xFF333333.toInt())
            val textColor = intent.getIntExtra("textColor", 0xFF000000.toInt())

            val settings = SettingsUiState(
                popupPosition = position,
                popupStyle = style,
                backgroundColor = backgroundColor,
                textColor = textColor,
                backgroundAlpha = intent.getFloatExtra("backgroundAlpha", 1.0f),
                textAlpha = intent.getFloatExtra("textAlpha", 1.0f),
                popupFontSize = intent.getIntExtra("fontSize", 100),
                scrollingSpeed = intent.getIntExtra("scrollingSpeed", 10),
            )
            
            // 直接获取PendingIntent
            val contentIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("contentIntent", android.app.PendingIntent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("contentIntent")
            }
            
            Log.d("OverlayService", "Received contentIntent: $contentIntent")
            Log.d("OverlayService", "ContentIntent target package: ${contentIntent?.targetPackage}")
            Log.d("OverlayService", "ContentIntent creator package: ${contentIntent?.creatorPackage}")
            
            val notificationData = NotificationData(
                settings = settings,
                duration = intent.getIntExtra("duration", 5),
                title = intent.getStringExtra("title"),
                text = intent.getStringExtra("text"),
                icon = intent.getByteArrayExtra("icon"),
                isOngoing = intent.getBooleanExtra("isOngoing", false),
                packageName = intent.getStringExtra("packageName"), // 获取包名
                contentIntent = contentIntent, // 直接使用传递的PendingIntent
                statusBarNotification = null // 不再使用StatusBarNotification
            )
            
            // 添加日志记录PendingIntent是否成功传递
            if (notificationData.contentIntent != null) {
                Log.d("OverlayService", "ContentIntent received for package: ${notificationData.packageName}")
            } else {
                Log.w("OverlayService", "No ContentIntent received for package: ${notificationData.packageName}")
            }
            
            synchronized(notificationQueue) {
                notificationQueue.clear()
                notificationQueue.offer(notificationData)
            }
            
            handler.post { processNotificationQueue() }
        }
        return START_NOT_STICKY
    }

    private fun processNotificationQueue() {
        val notificationData: NotificationData? = synchronized(notificationQueue) {
            notificationQueue.poll()
        }
        notificationData?.let { displayOrUpdateOverlay(it) }
    }

    private fun displayOrUpdateOverlay(notification: NotificationData) {
        currentNotificationId = notification.id
        currentNotificationData = notification // 保存当前通知数据
        
        // 检查是否需要更新弹窗位置或样式
        val shouldUpdatePosition = currentPopupPosition != notification.settings.popupPosition && overlayView != null
        val shouldUpdateStyle = currentPopupStyle != notification.settings.popupStyle && overlayView != null
        
        if (overlayView == null) {
            showOverlay(notification)
        } else {
            // 如果位置或样式改变了，需要重新创建overlayView而不是仅仅更新
            if (shouldUpdatePosition || shouldUpdateStyle) {
                removeOverlay()
                showOverlay(notification)
            } else {
                updateCurrentOverlay(notification)
            }
        }
        
        // 更新当前弹窗位置和样式
        currentPopupPosition = notification.settings.popupPosition
        currentPopupStyle = notification.settings.popupStyle

        removalRunnable?.let { handler.removeCallbacks(it) }
        if (!notification.isOngoing) {
            removalRunnable = Runnable { removeNotification(notification.id) }
            handler.postDelayed(removalRunnable!!, notification.duration * 1000L)
        }
    }

    private fun removeNotification(id: Long) {
        if (currentNotificationId == id) {
            removeOverlay()
            stopSelf()
        }
    }

    fun removeOngoingNotification() {
        removeOverlay()
        stopSelf()
    }

    private fun showOverlay(notification: NotificationData) {
        val gravity = if (notification.settings.popupPosition == PopupPosition.TOP) Gravity.TOP else Gravity.BOTTOM
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { this.gravity = gravity }

        // 创建手势检测器用于处理滑动动作
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                // 检测滑动动作：水平滑动且移动距离足够
                if (e1 != null) {
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y
                    
                    // 检查是否主要是水平滑动（水平移动距离大于垂直移动距离）
                    if (Math.abs(deltaX) > Math.abs(deltaY) && Math.abs(deltaX) > 150) {
                        // 左滑或右滑，移除通知
                        removeOverlay()
                        stopSelf()
                        return true
                    }
                }
                return false
            }
        })

        overlayView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            this.gravity = Gravity.CENTER_VERTICAL
            
            // 修复：正确处理背景颜色和透明度
            val backgroundColor = android.graphics.Color.argb(
                (notification.settings.backgroundAlpha * 255).toInt(),
                android.graphics.Color.red(notification.settings.backgroundColor),
                android.graphics.Color.green(notification.settings.backgroundColor),
                android.graphics.Color.blue(notification.settings.backgroundColor)
            )
            setBackgroundColor(backgroundColor)
            
            // 添加触摸监听器处理手势和点击
            setOnTouchListener { view, event ->
                // 将事件传递给手势检测器
                val gestureHandled = gestureDetector.onTouchEvent(event)
                
                // 如果手势检测器没有处理该事件，则处理点击事件
                if (!gestureHandled && event.action == MotionEvent.ACTION_UP) {
                    // 获取点击事件在视图中的坐标
                    val x = event.x
                    val y = event.y
                    
                    Log.d("OverlayService", "Notification clicked")
                    
                    // 直接使用传递的PendingIntent
                    val contentIntent = notification.contentIntent
                    Log.d("OverlayService", "Using contentIntent: $contentIntent")
                    
                    if (contentIntent != null) {
                        Log.d("OverlayService", "Attempting to open notification page for package: ${notification.packageName}")
                        openNotificationPage(contentIntent)
                    } else if (notification.packageName != null) {
                        Log.d("OverlayService", "Opening app directly for package: ${notification.packageName}")
                        openApp(notification.packageName)
                    } else {
                        Log.w("OverlayService", "No package name or content intent available")
                        Toast.makeText(this@OverlayService, "无法打开通知", Toast.LENGTH_SHORT).show()
                    }
                }
                
                true // 表示我们处理了这个事件
            }
        }

        // 根据样式设置不同的布局
        if (notification.settings.popupStyle == PopupStyle.BANNER) {
            showBannerStyle(notification)
        } else {
            showNarrowStyle(notification)
        }

        windowManager.addView(overlayView, params)
    }

    private fun showBannerStyle(notification: NotificationData) {
        overlayView?.apply {
            setPadding(32, 32, 32, 32)
        }
        val textColor = android.graphics.Color.argb(
            (notification.settings.textAlpha * 255).toInt(),
            android.graphics.Color.red(notification.settings.textColor),
            android.graphics.Color.green(notification.settings.textColor),
            android.graphics.Color.blue(notification.settings.textColor)
        )

        // 创建顶部容器，包含图标和标题
        val topContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        // 创建图标视图
        iconView = ImageView(this).apply {
            val iconBitmap = notification.icon?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } 
                ?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            setImageBitmap(iconBitmap)
            adjustViewBounds = true
        }

        // 创建标题视图
        titleView = TextView(this).apply {
            textSize = 16f * (notification.settings.popupFontSize / 100f)
            alpha = notification.settings.textAlpha
            setTextColor(textColor)
            text = notification.title ?: "测试标题"
        }

        // 将图标和标题添加到顶部容器
        topContainer.addView(iconView, LinearLayout.LayoutParams(48, 48))
        val spacer = View(this)
        topContainer.addView(spacer, LinearLayout.LayoutParams(16, 1))
        topContainer.addView(titleView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // 创建内容视图
        contentView = TextView(this).apply {
            textSize = 14f * (notification.settings.popupFontSize / 100f)
            alpha = notification.settings.textAlpha
            setTextColor(textColor)
            text = notification.text ?: "这是一条测试通知内容。"
        }

        // 添加视图到主容器
        overlayView?.addView(topContainer)
        overlayView?.addView(contentView)
    }

    private fun showNarrowStyle(notification: NotificationData) {
        overlayView?.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL // 确保垂直居中
            setPadding(20, 10, 20, 10)
        }
        val textColor = android.graphics.Color.argb(
            (notification.settings.textAlpha * 255).toInt(),
            android.graphics.Color.red(notification.settings.textColor),
            android.graphics.Color.green(notification.settings.textColor),
            android.graphics.Color.blue(notification.settings.textColor)
        )
        
        // 根据字体大小计算尺寸
        val textSizeSp = 14f * (notification.settings.popupFontSize / 100f)
        val iconSizePx = (textSizeSp * resources.displayMetrics.scaledDensity).toInt()

        // 创建图标视图（极窄样式中不滑动的图标）
        iconView = ImageView(this).apply {
            val iconBitmap = notification.icon?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } 
                ?: BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
            setImageBitmap(iconBitmap)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        // 创建文本视图
        textView = TextView(this).apply {
            textSize = textSizeSp
            alpha = notification.settings.textAlpha
            setTextColor(textColor)
            isSingleLine = true
        }

        // 添加视图（极窄样式包含一个不滑动的图标，后跟滚动文本）
        overlayView?.addView(iconView, LinearLayout.LayoutParams(iconSizePx, iconSizePx))
        val spacer = View(this)
        overlayView?.addView(spacer, LinearLayout.LayoutParams(16, 1))
        overlayView?.addView(textView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        
        textView?.let { startMarqueeAnimation(it, notification) }
    }

    private fun updateCurrentOverlay(notification: NotificationData) {
        currentNotificationData = notification // 更新当前通知数据
        val textColor = android.graphics.Color.argb(
            (notification.settings.textAlpha * 255).toInt(),
            android.graphics.Color.red(notification.settings.textColor),
            android.graphics.Color.green(notification.settings.textColor),
            android.graphics.Color.blue(notification.settings.textColor)
        )
        
        overlayView?.apply {
            // 修复：正确处理背景颜色和透明度
            val backgroundColor = android.graphics.Color.argb(
                (notification.settings.backgroundAlpha * 255).toInt(),
                android.graphics.Color.red(notification.settings.backgroundColor),
                android.graphics.Color.green(notification.settings.backgroundColor),
                android.graphics.Color.blue(notification.settings.backgroundColor)
            )
            setBackgroundColor(backgroundColor)
            
            // 根据样式设置方向
            orientation = if (notification.settings.popupStyle == PopupStyle.BANNER) {
                LinearLayout.VERTICAL
            } else {
                LinearLayout.HORIZONTAL
            }
        }
        
        // 更新文本视图属性
        if (notification.settings.popupStyle == PopupStyle.BANNER) {
            // 更新横幅样式文本
            titleView?.apply {
                textSize = 16f * (notification.settings.popupFontSize / 100f)
                alpha = notification.settings.textAlpha
                setTextColor(textColor)
                text = notification.title ?: "测试标题"
            }
            
            contentView?.apply {
                textSize = 14f * (notification.settings.popupFontSize / 100f)
                alpha = notification.settings.textAlpha
                setTextColor(textColor)
                text = notification.text ?: "这是一条测试通知内容。"
            }
            
            // 确保textView为空（如果之前是极窄样式）
            textView?.text = ""
        } else {
            // 更新极窄样式
            val textSizeSp = 14f * (notification.settings.popupFontSize / 100f)
            val iconSizePx = (textSizeSp * resources.displayMetrics.scaledDensity).toInt()

            textView?.apply {
                textSize = textSizeSp
                alpha = notification.settings.textAlpha
                setTextColor(textColor)
            }

            // 更新图标大小
            iconView?.layoutParams = iconView?.layoutParams?.apply {
                width = iconSizePx
                height = iconSizePx
            }

            overlayView?.apply {
                setPadding(20, 10, 20, 10)
                gravity = Gravity.CENTER_VERTICAL
            }
            
            // 确保标题和内容视图为空（如果之前是横幅样式）
            titleView?.text = ""
            contentView?.text = ""
        }
        
        // 如果是极窄样式，启动滚动动画
        if (notification.settings.popupStyle == PopupStyle.NARROW) {
            textView?.let { startMarqueeAnimation(it, notification) }
        } else {
            // 如果是横幅样式，取消可能正在进行的滚动动画
            marqueeAnimator?.cancel()
            startMarqueeRunnable?.let { handler.removeCallbacks(it) }
            
            overlayView?.apply {
                setPadding(32, 32, 32, 32)
            }
        }
    }

    private fun getNotificationDisplayText(notification: NotificationData): String {
        return "${notification.title ?: "测试标题"}: ${notification.text ?: "这是一条测试通知内容"}"
    }

    private fun startMarqueeAnimation(textView: TextView, notification: NotificationData) {
        marqueeAnimator?.cancel()
        startMarqueeRunnable?.let { handler.removeCallbacks(it) }
        textView.scrollX = 0

        textView.textSize = 14f * (notification.settings.popupFontSize / 100f)
        textView.alpha = notification.settings.textAlpha
        textView.isSingleLine = true

        // 极窄样式包含一个不滑动的图标，后跟滚动文本（文本中不再包含图标）
        val message = getNotificationDisplayText(notification)
        textView.text = message

        textView.post {
            val textWidth = textView.paint.measureText(message)
            val viewWidth = textView.width

            if (textWidth <= viewWidth) {
                return@post
            }

            startMarqueeRunnable = Runnable {
                val separator = "      "
                val duplicatedText = TextUtils.concat(message, separator, message)
                textView.text = duplicatedText

                val separatorWidth = textView.paint.measureText(separator)
                val scrollDistance = textWidth + separatorWidth

                val pixelsPerSecond = (notification.settings.scrollingSpeed * 20).coerceAtLeast(1)
                val duration = (scrollDistance / pixelsPerSecond * 1000).toLong()

                marqueeAnimator = ObjectAnimator.ofInt(textView, "scrollX", 0, scrollDistance.toInt()).apply {
                    this.duration = duration
                    this.interpolator = LinearInterpolator()
                    repeatCount = ObjectAnimator.INFINITE
                    repeatMode = ObjectAnimator.RESTART
                    start()
                }
            }
            handler.postDelayed(startMarqueeRunnable!!, 1000L)
        }
    }

    private class CenteredImageSpan(drawable: Drawable) : ImageSpan(drawable) {
        override fun draw(
            canvas: Canvas,
            text: CharSequence,
            start: Int,
            end: Int,
            x: Float,
            top: Int,
            y: Int,
            bottom: Int,
            paint: Paint
        ) {
            val b = drawable
            val fm = paint.fontMetricsInt
            val transY = ((y + fm.descent + y + fm.ascent) / 2 - b.bounds.height() / 2).toFloat()
            canvas.save()
            canvas.translate(x, transY)
            b.draw(canvas)
            canvas.restore()
        }
    }

    private fun removeOverlay() {
        marqueeAnimator?.cancel()
        startMarqueeRunnable?.let { handler.removeCallbacks(it) }
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
        textView = null
        iconView = null
        titleView = null
        contentView = null
        currentNotificationId = null
        currentNotificationData = null
        removalRunnable?.let { handler.removeCallbacks(it) }
        startMarqueeRunnable = null
        marqueeAnimator = null
        removalRunnable = null
    }

    // 跳转到通知的具体页面
    private fun openNotificationPage(pendingIntent: android.app.PendingIntent) {
        try {
            Log.d("OverlayService", "Sending PendingIntent: $pendingIntent")
            Log.d("OverlayService", "PendingIntent target package: ${pendingIntent.targetPackage}")
            Log.d("OverlayService", "PendingIntent creator package: ${pendingIntent.creatorPackage}")

            // 检查PendingIntent是否有效
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (pendingIntent.isActivity) {
                    Log.d("OverlayService", "PendingIntent is for an Activity")
                } else if (pendingIntent.isBroadcast) {
                    Log.d("OverlayService", "PendingIntent is for a Broadcast")
                } else if (pendingIntent.isService) {
                    Log.d("OverlayService", "PendingIntent is for a Service")
                }
            }

            // To launch an activity from a non-activity context (like a service),
            // we must add the FLAG_ACTIVITY_NEW_TASK flag. If the activity is already
            // running, FLAG_ACTIVITY_REORDER_TO_FRONT will bring it to the foreground.
            val fillInIntent = Intent().apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }

            // 发送PendingIntent
            pendingIntent.send(this@OverlayService, 0, fillInIntent)

            Log.d("OverlayService", "PendingIntent sent successfully")

            // 移除通知
            removeOverlay()
            stopSelf()
        } catch (e: android.app.PendingIntent.CanceledException) {
            Log.e("OverlayService", "PendingIntent was canceled: ${e.message}", e)
            Toast.makeText(this, "通知意图已被取消", Toast.LENGTH_SHORT).show()
            // 如果PendingIntent被取消，则尝试通过包名打开应用
            currentNotificationData?.packageName?.let { packageName ->
                openApp(packageName)
            }
        } catch (e: SecurityException) {
            Log.e("OverlayService", "SecurityException when sending PendingIntent: ${e.message}", e)
            Toast.makeText(this, "权限不足，无法打开通知页面: ${e.message}", Toast.LENGTH_LONG).show()
            // 如果权限不足，则尝试通过包名打开应用
            currentNotificationData?.packageName?.let { packageName ->
                openApp(packageName)
            }
        } catch (e: Exception) {
            Log.e("OverlayService", "Error sending PendingIntent: ${e.message}", e)
            Toast.makeText(this, "打开通知页面时出错: ${e.message}", Toast.LENGTH_LONG).show()
            // 如果PendingIntent无法使用，则尝试通过包名打开应用
            currentNotificationData?.packageName?.let { packageName ->
                openApp(packageName)
            }
        }
    }

    // 跳转到指定应用
    private fun openApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                // 移除通知
                removeOverlay()
                stopSelf()
            } else {
                Toast.makeText(this, "无法打开应用: $packageName", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "打开应用时出错: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        removeOverlay()
        synchronized(notificationQueue) { notificationQueue.clear() }
    }
}
