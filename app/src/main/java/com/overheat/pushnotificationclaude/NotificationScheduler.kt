package com.overheat.pushnotificationclaude

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.PowerManager
import java.util.Calendar
import java.util.TimeZone
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// Data classes for schedule management
data class ScheduleItem(
    val time: String,
    val message: String,
    val hour: Int,
    val minute: Int
) {
    constructor(time: String, message: String) : this(
        time = time,
        message = message,
        hour = time.split(":")[0].toInt(),
        minute = time.split(":")[1].toInt()
    )
}

data class NotificationSchedules(
    val weekdaySchedules: List<ScheduleItem>,
    val fridaySchedules: List<ScheduleItem>
)

class NotificationScheduler(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("attendance_prefs", Context.MODE_PRIVATE)

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val gson = Gson()

    // Set Indonesia WIB timezone (UTC+7)
    private val indonesiaTimeZone = TimeZone.getTimeZone("Asia/Jakarta")

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "attendance_channel"
        const val PREF_NOTIFICATION_ENABLED = "notification_enabled"
        const val PREF_WEEKDAY_SCHEDULES = "weekday_schedules"
        const val PREF_FRIDAY_SCHEDULES = "friday_schedules"

        // Base request codes
        const val REQUEST_CODE_BASE_WEEKDAY = 1000
        const val REQUEST_CODE_BASE_FRIDAY = 2000
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Attendance Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for attendance reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 1000, 500, 1000, 500, 1000)
                setBypassDnd(true) // Bypass Do Not Disturb
                setShowBadge(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Check if the app can schedule exact alarms
    fun canScheduleExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // Request permission to ignore battery optimization
    fun requestIgnoreBatteryOptimization(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                }
            } else null
        } else null
    }

    fun getSchedules(): NotificationSchedules {
        val weekdayJson = sharedPreferences.getString(PREF_WEEKDAY_SCHEDULES, null)
        val fridayJson = sharedPreferences.getString(PREF_FRIDAY_SCHEDULES, null)

        val weekdaySchedules = if (weekdayJson != null) {
            gson.fromJson(weekdayJson, object : TypeToken<List<ScheduleItem>>() {}.type)
        } else {
            getDefaultWeekdaySchedules()
        }

        val fridaySchedules = if (fridayJson != null) {
            gson.fromJson(fridayJson, object : TypeToken<List<ScheduleItem>>() {}.type)
        } else {
            getDefaultFridaySchedules()
        }

        return NotificationSchedules(weekdaySchedules, fridaySchedules)
    }

    fun saveSchedules(schedules: NotificationSchedules) {
        val weekdayJson = gson.toJson(schedules.weekdaySchedules)
        val fridayJson = gson.toJson(schedules.fridaySchedules)

        sharedPreferences.edit {
            putString(PREF_WEEKDAY_SCHEDULES, weekdayJson)
                .putString(PREF_FRIDAY_SCHEDULES, fridayJson)
        }
    }

    private fun getDefaultWeekdaySchedules(): List<ScheduleItem> {
        return listOf(
            ScheduleItem("07:25", "Waktunya Absen Pagi"),
            ScheduleItem("13:00", "Waktunya Absen Siang"),
            ScheduleItem("16:15", "Waktunya Absen Sore")
        )
    }

    private fun getDefaultFridaySchedules(): List<ScheduleItem> {
        return listOf(
            ScheduleItem("07:10", "Waktunya Absen Pagi"),
            ScheduleItem("11:45", "Waktunya Absen Sore")
        )
    }

    fun scheduleAllNotifications() {
        // Cancel existing notifications first
        cancelAllNotifications()

        // Save enabled state
        sharedPreferences.edit { putBoolean(PREF_NOTIFICATION_ENABLED, true) }

        val schedules = getSchedules()

        // Schedule Monday-Thursday notifications
        val weekdays = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY)
        weekdays.forEachIndexed { dayIndex, dayOfWeek ->
            schedules.weekdaySchedules.forEachIndexed { scheduleIndex, schedule ->
                val requestCode = REQUEST_CODE_BASE_WEEKDAY + (dayIndex * 10) + scheduleIndex
                scheduleWeekdayNotification(
                    dayOfWeek,
                    schedule.hour,
                    schedule.minute,
                    schedule.message,
                    requestCode
                )
            }
        }

        // Schedule Friday notifications
        schedules.fridaySchedules.forEachIndexed { scheduleIndex, schedule ->
            val requestCode = REQUEST_CODE_BASE_FRIDAY + scheduleIndex
            scheduleWeekdayNotification(
                Calendar.FRIDAY,
                schedule.hour,
                schedule.minute,
                schedule.message,
                requestCode
            )
        }
    }

    private fun scheduleWeekdayNotification(dayOfWeek: Int, hour: Int, minute: Int, message: String, requestCode: Int) {
        // Create calendar with Indonesia WIB timezone
        val calendar = Calendar.getInstance(indonesiaTimeZone).apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val currentTime = Calendar.getInstance(indonesiaTimeZone)
        val currentDayOfWeek = currentTime.get(Calendar.DAY_OF_WEEK)

        // If it's the same day of week as today
        if (currentDayOfWeek == dayOfWeek) {
            // If the scheduled time hasn't passed today, schedule for today
            if (calendar.after(currentTime)) {
                // Keep today's date, time is already set above
            } else {
                // Time has passed today, schedule for next week
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
        } else {
            // Different day of week - find the next occurrence
            calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)

            // If the calculated day is before today, move to next week
            if (calendar.before(currentTime)) {
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("message", message)
            putExtra("request_code", requestCode)
            putExtra("day_of_week", dayOfWeek)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            // Use setExactAndAllowWhileIdle for better reliability
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }

            // Debug log to verify the scheduled time
            android.util.Log.d("NotificationScheduler",
                "Scheduled notification for ${calendar.time} (WIB) - Day: $dayOfWeek, Current Day: $currentDayOfWeek, Time: $hour:$minute, Message: $message, RequestCode: $requestCode")

        } catch (e: SecurityException) {
            android.util.Log.e("NotificationScheduler", "Failed to schedule exact alarm: ${e.message}")
            // Fallback to inexact alarm
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelAllNotifications() {
        sharedPreferences.edit { putBoolean(PREF_NOTIFICATION_ENABLED, false) }

        // Cancel all possible request codes
        for (i in REQUEST_CODE_BASE_WEEKDAY..REQUEST_CODE_BASE_WEEKDAY + 50) {
            cancelNotification(i)
        }

        for (i in REQUEST_CODE_BASE_FRIDAY..REQUEST_CODE_BASE_FRIDAY + 10) {
            cancelNotification(i)
        }
    }

    private fun cancelNotification(requestCode: Int) {
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun isNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean(PREF_NOTIFICATION_ENABLED, false)
    }
}