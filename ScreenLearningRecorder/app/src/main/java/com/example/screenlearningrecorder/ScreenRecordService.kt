package com.example.screenlearningrecorder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ScreenRecordService : Service() {
    private var recorder: MediaRecorder? = null
    private var projection: MediaProjection? = null
    private var activeOutput: File? = null
    private var activeSession: File? = null
    private var isRecording = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent)
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun startRecording(intent: Intent) {
        if (isRecording) return
        createNotificationChannel()
        val notification = buildNotification("กำลังบันทึกหน้าจอ", true)
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(NOTIFICATION_ID, notification)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(EXTRA_DATA, Intent::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        if (resultData == null || resultCode == 0) { stopSelf(); return }

        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
            val width = (metrics.widthPixels / 2) * 2
            val height = (metrics.heightPixels / 2) * 2
            val density = metrics.densityDpi
            val root = File(getExternalFilesDir(null), "ScreenLearningRecorder/Sessions")
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            activeSession = File(root, "Session_$stamp").apply { mkdirs() }
            activeOutput = File(activeSession, "screen_recording.mp4")

            recorder = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            recorder!!.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(activeOutput!!.absolutePath)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width.coerceAtLeast(2), height.coerceAtLeast(2))
                setVideoFrameRate(24)
                setVideoEncodingBitRate(4_000_000)
                prepare()
            }
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, resultData)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { if (isRecording) stopRecording() }
            }, null)
            projection?.createVirtualDisplay("ScreenLearningRecorder", width, height, density,
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                recorder!!.surface, null, null)
            recorder!!.start()
            isRecording = true
            sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra("recording", true))
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification("กำลังบันทึกหน้าจอ · แตะเพื่อหยุด", true))
        } catch (e: Exception) {
            isRecording = false
            try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
            recorder = null
            projection?.stop(); projection = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        if (!isRecording) { stopSelf(); return }
        isRecording = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.reset(); recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName).putExtra("recording", false))
        val session = activeSession
        val video = activeOutput
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        if (session != null && video != null && video.exists() && video.length() > 0) {
            Thread { extractChangeSnapshots(video, session) }.start()
        }
    }

    private fun extractChangeSnapshots(video: File, session: File) {
        val shotsDir = File(session, "screenshots").apply { mkdirs() }
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            var previous: Bitmap? = null
            var saved = 0
            var time = 0L
            while (time < durationMs && saved < 1200) {
                val frame = retriever.getFrameAtTime(time * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (frame != null) {
                    val small = Bitmap.createScaledBitmap(frame, 32, 32, true)
                    val changed = previous == null || difference(previous!!, small) > 0.075
                    if (changed) {
                        val out = File(shotsDir, "frame_${time.toString().padStart(8, '0')}ms.jpg")
                        FileOutputStream(out).use { frame.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                        saved++
                    }
                    previous?.recycle()
                    previous = small
                    if (frame !== small) frame.recycle()
                }
                time += 1000L
            }
            previous?.recycle()
            File(session, "session.txt").writeText(
                "video=${video.name}\n" +
                "duration_ms=$durationMs\n" +
                "snapshots=$saved\n" +
                "snapshot_interval_ms=1000\n" +
                "note=Snapshots are sampled from the completed video and filtered by visual difference.\n"
            )
        } catch (_: Exception) {
            File(session, "processing_error.txt").writeText("ไม่สามารถสร้างภาพจากวิดีโอได้ โปรดลองเปิดวิดีโอและตรวจสอบพื้นที่จัดเก็บ")
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun difference(a: Bitmap, b: Bitmap): Double {
        val pa = IntArray(32 * 32); val pb = IntArray(32 * 32)
        a.getPixels(pa, 0, 32, 0, 0, 32, 32); b.getPixels(pb, 0, 32, 0, 0, 32, 32)
        var sum = 0L
        for (i in pa.indices) {
            sum += abs(((pa[i] shr 16) and 255) - ((pb[i] shr 16) and 255))
            sum += abs(((pa[i] shr 8) and 255) - ((pb[i] shr 8) and 255))
            sum += abs((pa[i] and 255) - (pb[i] and 255))
        }
        return sum.toDouble() / (pa.size * 3.0 * 255.0)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen recording", NotificationManager.IMPORTANCE_LOW).apply {
                description = "สถานะและการควบคุมการบันทึกหน้าจอ"
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, recording: Boolean): Notification {
        val stopIntent = Intent(this, ScreenRecordService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openIntent = PendingIntent.getActivity(this, 3, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentTitle("Screen Learning Recorder")
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(recording)
            .setOnlyAlertOnce(true)
        if (recording) builder.addAction(android.R.drawable.ic_media_pause, "หยุดบันทึก", stopPending)
        return builder.build()
    }

    override fun onDestroy() {
        if (isRecording) {
            try { recorder?.stop() } catch (_: Exception) {}
            try { recorder?.release() } catch (_: Exception) {}
            try { projection?.stop() } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.screenlearningrecorder.START"
        const val ACTION_STOP = "com.example.screenlearningrecorder.STOP"
        const val ACTION_STATE = "com.example.screenlearningrecorder.STATE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        private const val CHANNEL_ID = "screen_recording_channel"
        private const val NOTIFICATION_ID = 7101
    }
}
