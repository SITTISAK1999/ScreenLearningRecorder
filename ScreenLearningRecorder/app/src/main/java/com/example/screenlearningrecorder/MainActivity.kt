package com.example.screenlearningrecorder

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private val requestCapture = 501
    private val requestOverlay = 502
    private val requestNotifications = 503

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestNotificationPermissionIfNeeded()
        refreshSessions()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF10131A.toInt())
        }
        val title = TextView(this).apply {
            text = "Screen Learning Recorder"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        }
        val subtitle = TextView(this).apply {
            text = "บันทึกหน้าจอเพื่อทบทวนการเรียนรู้ · เก็บข้อมูลในเครื่องก่อน"
            textSize = 14f
            setTextColor(0xFFB7C0D1.toInt())
            setPadding(0, 8, 0, 18)
        }
        status = TextView(this).apply {
            text = "สถานะ: พร้อมใช้งาน"
            textSize = 15f
            setTextColor(0xFF8FE3B0.toInt())
            setPadding(0, 0, 0, 16)
        }
        root.addView(title)
        root.addView(subtitle)
        root.addView(status)
        root.addView(button("เริ่มบันทึกหน้าจอ") { beginCapturePermission() })
        root.addView(button("เปิดปุ่มลอย") { enableFloatingButton() })
        root.addView(button("รีเฟรชรายการบันทึก") { refreshSessions() })
        root.addView(button("แชร์แอป / แชร์ไฟล์") { shareAppInfo() })
        root.addView(button("Google Drive — ขั้นตอนเชื่อมต่อถัดไป") {
            Toast.makeText(this, "รุ่นเริ่มต้นบันทึกในเครื่องก่อน การเชื่อม Google Drive ต้องตั้งค่า OAuth/API เพิ่มเติม", Toast.LENGTH_LONG).show()
        })
        val label = TextView(this).apply {
            text = "รายการ Session ในเครื่อง"
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 22, 0, 8)
        }
        root.addView(label)
        val scroll = ScrollView(this)
        val sessionList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "sessions" }
        scroll.addView(sessionList)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun button(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 7 }
    }

    private fun beginCapturePermission() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "อนุญาตปุ่มลอยได้ในหน้าถัดไป หรือเริ่มบันทึกโดยไม่ใช้ปุ่มลอยก็ได้", Toast.LENGTH_LONG).show()
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    private fun enableFloatingButton() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivityForResult(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")), requestOverlay)
        } else {
            startService(Intent(this, FloatingButtonService::class.java))
            Toast.makeText(this, "เปิดปุ่มลอยแล้ว", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Activity result API retained for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCapture) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_START
                    putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(ScreenRecordService.EXTRA_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
                status.text = "สถานะ: กำลังเริ่มบันทึก"
                if (Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) startService(Intent(this, FloatingButtonService::class.java))
            } else {
                status.text = "สถานะ: ยกเลิกการอนุญาตบันทึกหน้าจอ"
            }
        } else if (requestCode == requestOverlay && Build.VERSION.SDK_INT >= 23 && Settings.canDrawOverlays(this)) {
            startService(Intent(this, FloatingButtonService::class.java))
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), requestNotifications)
        }
    }

    private fun refreshSessions() {
        val list = findViewById<View>(android.R.id.content).findViewWithTag<LinearLayout>("sessions") ?: return
        list.removeAllViews()
        val base = File(getExternalFilesDir(null), "ScreenLearningRecorder/Sessions")
        val files = base.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "ยังไม่มีรายการบันทึก\nไฟล์จะเก็บในพื้นที่ของแอป"
                setTextColor(0xFFB7C0D1.toInt())
                setPadding(0, 8, 0, 12)
            })
        } else {
            files.forEach { dir ->
                val video = File(dir, "screen_recording.mp4")
                val shots = File(dir, "screenshots").listFiles()?.size ?: 0
                list.addView(TextView(this).apply {
                    text = "${dir.name}\nวิดีโอ: ${if (video.exists()) "พร้อม" else "กำลังประมวลผล/ไม่มีไฟล์"} · ภาพที่สกัดได้: $shots"
                    textSize = 14f
                    setTextColor(0xFFFFFFFF.toInt())
                    setPadding(12, 12, 12, 12)
                    setBackgroundColor(0xFF202633.toInt())
                }, LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 8 })
            }
        }
    }

    private fun shareAppInfo() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Screen Learning Recorder — แอปบันทึกหน้าจอเพื่อทบทวนการเรียนรู้")
        }
        startActivity(Intent.createChooser(send, "แชร์แอป"))
    }
}
