package com.codex.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.codex.app.MainActivity
import com.codex.app.R

class AgentService : Service() {
    companion object {
        const val CHANNEL_ID = "codex_agent_channel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.codex.action.START"
        const val ACTION_STOP = "com.codex.action.STOP"
        const val EXTRA_MESSAGE = "extra_message"
    }

    private val binder = AgentBinder()

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val message = intent.getStringExtra(EXTRA_MESSAGE) ?: ""
                startForeground(NOTIFICATION_ID, createNotification("运行中", "Agent 正在执行任务..."))
                // Start agent processing here
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Codex Agent",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示 Agent 的运行状态"
        }
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(title: String, content: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(createPendingIntent())
            .build()

    private fun createPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    inner class AgentBinder : android.os.Binder() {
        fun getService(): AgentService = this@AgentService
    }
}