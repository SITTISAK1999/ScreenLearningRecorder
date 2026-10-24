package com.example.screenlearningrecorder

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import kotlin.math.abs

class FloatingButtonService : Service() {
    private lateinit var wm: WindowManager
    private var bubble: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var recording = false

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            recording = intent?.getBooleanExtra("recording", false) ?: false
            if (recording) removeBubble() else showBubble()
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { stopSelf(); return }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val filter = IntentFilter(ScreenRecordService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED) else @Suppress("DEPRECATION") registerReceiver(stateReceiver, filter)
        showBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!recording) showBubble()
        return START_STICKY
    }

    private fun showBubble() {
        if (bubble != null || (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this))) return
        val view = TextView(this).apply {
            text = "SLR"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xEE2962FF.toInt()); setStroke(2, Color.WHITE) }
            elevation = 8f
        }
        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        val lp = WindowManager.LayoutParams(58, 58, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 18; y = 220
        }
        var downX = 0; var downY = 0; var touchX = 0f; var touchY = 0f
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { downX = lp.x; downY = lp.y; touchX = event.rawX; touchY = event.rawY; true }
                MotionEvent.ACTION_MOVE -> { lp.x = downX + (event.rawX - touchX).toInt(); lp.y = downY + (event.rawY - touchY).toInt(); try { wm.updateViewLayout(view, lp) } catch (_: Exception) {}; true }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - touchX) < 8 && abs(event.rawY - touchY) < 8) {
                        val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(open)
                    }
                    true
                }
                else -> false
            }
        }
        try { wm.addView(view, lp); bubble = view; params = lp } catch (_: Exception) { bubble = null }
    }

    private fun removeBubble() {
        val current = bubble ?: return
        try { wm.removeView(current) } catch (_: Exception) {}
        bubble = null; params = null
    }

    override fun onDestroy() {
        removeBubble()
        try { unregisterReceiver(stateReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
