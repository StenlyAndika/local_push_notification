package com.overheat.pushnotificationclaude

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import java.util.Calendar

class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val message = intent.getStringExtra("message") ?: "Attendance Reminder"
        val requestCode = intent.getIntExtra("request_code", 0)
        val dayOfWeek = intent.getIntExtra("day_of_week", -1)

        android.util.Log.d("NotificationReceiver", "Received notification: $message, RequestCode: $requestCode")

        // Wake up the device
        wakeUpDevice(context)

        // Show notification
        showNotification(context, message, requestCode)

        // Vibrate
        vibrateDevice(context)

        // Reschedule for next week if this was a weekly alarm
        if (dayOfWeek != -1) {
            rescheduleNextWeek(context, intent, dayOfWeek)
        }
    }

    private fun rescheduleNextWeek(context: Context, originalIntent: Intent, dayOfWeek: Int) {
        val message = originalIntent.getStringExtra("message") ?: "Attendance Reminder"
        val requestCode = originalIntent.getIntExtra("request_code", 0)

        // Extract hour and minute from the original schedule
        val notificationScheduler = NotificationScheduler(context)
        val schedules = notificationScheduler.getSchedules()

        // Find the matching schedule item to get the time
        val allSchedules = schedules.weekdaySchedules + schedules.fridaySchedules
        val matchingSchedule = findMatchingSchedule(allSchedules, message)

        if (matchingSchedule != null) {
            // Schedule for next week
            val calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Jakarta")).apply {
                add(Calendar.WEEK_OF_YEAR, 1)
                set(Calendar.DAY_OF_WEEK, dayOfWeek)
                set(Calendar.HOUR_OF_DAY, matchingSchedule.hour)
                set(Calendar.MINUTE, matchingSchedule.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val newIntent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("message", message)
                putExtra("request_code", requestCode)
                putExtra("day_of_week", dayOfWeek)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                newIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }

                android.util.Log.d("NotificationReceiver", "Rescheduled for next week: ${calendar.time}")
            } catch (e: SecurityException) {
                android.util.Log.e("NotificationReceiver", "Failed to reschedule: ${e.message}")
            }
        }
    }

    private fun findMatchingSchedule(schedules: List<ScheduleItem>, message: String): ScheduleItem? {
        return schedules.find { it.message == message }
    }

    private fun wakeUpDevice(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "AttendanceApp:WakeLock"
            )
            wakeLock.acquire(15000) // Keep awake for 15 seconds

            // Turn on screen even if device is locked
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
                try {
                    val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (keyguardManager.isKeyguardLocked) {
                        @Suppress("DEPRECATION")
                        val keyguardLock = keyguardManager.newKeyguardLock("AttendanceApp")
                        @Suppress("DEPRECATION")
                        keyguardLock.disableKeyguard()

                        // Re-enable keyguard after a delay
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            @Suppress("DEPRECATION")
                            keyguardLock.reenableKeyguard()
                        }, 10000)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationReceiver", "Failed to disable keyguard: ${e.message}")
                }
            }

            // Release wake lock after a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("NotificationReceiver", "Failed to release wake lock: ${e.message}")
                }
            }, 15000)

        } catch (e: Exception) {
            android.util.Log.e("NotificationReceiver", "Failed to wake up device: ${e.message}")
        }
    }

    private fun showNotification(context: Context, message: String, requestCode: Int) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationScheduler.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Attendance Reminder")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Use MAX instead of HIGH
            .setCategory(NotificationCompat.CATEGORY_ALARM) // Set as alarm category
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setLights(0xFF0000FF.toInt(), 1000, 500) // Blue light
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Show on lock screen
            .setFullScreenIntent(pendingIntent, true) // Try to show as full screen
            .build()

        notificationManager.notify(requestCode, notification)
    }

    private fun vibrateDevice(context: Context) {
        try {
            val vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                val vibrator = vibratorManager.defaultVibrator
                vibrator.vibrate(android.os.VibrationEffect.createWaveform(vibrationPattern, -1))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(vibrationPattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(vibrationPattern, -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NotificationReceiver", "Failed to vibrate: ${e.message}")
        }
    }
}