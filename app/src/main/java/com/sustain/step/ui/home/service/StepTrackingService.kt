package com.sustain.step.ui.home.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.sustain.step.MainActivity
import com.sustain.step.R
import com.sustain.step.di.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class StepTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val app: App
        get() = applicationContext as App

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(TRACKING_NOTIFICATION_ID, buildTrackingNotification())
        startStepTracking()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        app.stepsRepo.stopCounting()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startStepTracking() {
        serviceScope.launch(Dispatchers.IO) {
            app.stepsRepo.emitActualData()
            app.stepsRepo.startCounting()
            app.stepsRepo.stepsData.collectLatest { data ->
                val steps = data?.stepsCount ?: return@collectLatest
                val milestone = app.settings.consumeDailyStepsGoalMilestone(steps)
                if (milestone != null) {
                    showDailyGoalMilestoneNotification(steps, milestone)
                }
            }
        }
    }

    private fun showDailyGoalMilestoneNotification(steps: Int, milestone: Int) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val goal = app.settings.dailyStepsGoal
        val notification = NotificationCompat.Builder(this, GOAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_green_20)
            .setContentTitle(getDailyGoalMilestoneTitle(milestone))
            .setContentText(
                getString(
                    getDailyGoalMilestoneTextRes(milestone),
                    formatWithCommas(steps),
                    formatWithCommas(goal)
                )
            )
            .setContentIntent(mainActivityPendingIntent(GOAL_NOTIFICATION_REQUEST_CODE))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        manager.notify(GOAL_NOTIFICATION_ID + milestone, notification)
    }

    private fun getDailyGoalMilestoneTitle(milestone: Int): String {
        return getString(
            when (milestone) {
                50 -> R.string.daily_steps_goal_notification_title_50
                75 -> R.string.daily_steps_goal_notification_title_75
                else -> R.string.daily_steps_goal_notification_title
            }
        )
    }

    private fun getDailyGoalMilestoneTextRes(milestone: Int): Int {
        return when (milestone) {
            50 -> R.string.daily_steps_goal_notification_text_50
            75 -> R.string.daily_steps_goal_notification_text_75
            else -> R.string.daily_steps_goal_notification_text
        }
    }

    private fun buildTrackingNotification(): Notification {
        return NotificationCompat.Builder(this, TRACKING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(getString(R.string.step_tracking_notification_title))
            .setContentText(getString(R.string.step_tracking_notification_text))
            .setContentIntent(mainActivityPendingIntent(TRACKING_NOTIFICATION_REQUEST_CODE))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun mainActivityPendingIntent(requestCode: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(TRACKING_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    TRACKING_CHANNEL_ID,
                    getString(R.string.step_tracking_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        if (manager.getNotificationChannel(GOAL_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    GOAL_CHANNEL_ID,
                    getString(R.string.daily_steps_goal_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    private fun formatWithCommas(value: Int): String = String.format("%,d", value)

    companion object {
        private const val ACTION_STOP = "com.sustain.step.action.STOP_STEP_TRACKING"
        private const val TRACKING_CHANNEL_ID = "step_tracking_channel"
        private const val GOAL_CHANNEL_ID = "daily_steps_goal_channel"
        private const val TRACKING_NOTIFICATION_ID = 2001
        private const val GOAL_NOTIFICATION_ID = 2002
        private const val TRACKING_NOTIFICATION_REQUEST_CODE = 2101
        private const val GOAL_NOTIFICATION_REQUEST_CODE = 2102

        fun start(context: Context) {
            val intent = Intent(context, StepTrackingService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepTrackingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
