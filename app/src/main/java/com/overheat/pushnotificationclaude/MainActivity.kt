package com.overheat.pushnotificationclaude

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.overheat.pushnotificationclaude.ui.theme.PushNotificationClaudeTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle permission results if needed
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Some permissions were denied, you might want to show a dialog
            android.util.Log.w("MainActivity", "Some permissions were denied")

            // Show a user-friendly Toast (optional, for user feedback)
            Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
        }
    }

    private val exactAlarmPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle exact alarm permission result
        val notificationScheduler = NotificationScheduler(this)
        if (notificationScheduler.canScheduleExactAlarms()) {
            android.util.Log.d("MainActivity", "Exact alarm permission granted")

            // Show a user-friendly Toast (optional, for user feedback)
            Toast.makeText(this, "Exact alarm permission granted", Toast.LENGTH_LONG).show()
        } else {
            android.util.Log.w("MainActivity", "Exact alarm permission denied")

            // Show a user-friendly Toast (optional, for user feedback)
            Toast.makeText(this, "Exact alarm permission denied", Toast.LENGTH_LONG).show()
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Handle battery optimization result
        android.util.Log.d("MainActivity", "Battery optimization request completed")

        // Show a user-friendly Toast (optional, for user feedback)
        Toast.makeText(this, "Battery optimization request completed", Toast.LENGTH_LONG).show()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request necessary permissions
        requestPermissions()

        enableEdgeToEdge()
        setContent {
            PushNotificationClaudeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AttendanceScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Wake lock permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.WAKE_LOCK)
        }

        // Boot completed permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_BOOT_COMPLETED)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECEIVE_BOOT_COMPLETED)
        }

        // Vibrate permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.VIBRATE)
        }

        // Request regular permissions first
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }

        // Request exact alarm permission separately (Android 12+)
        requestExactAlarmPermission()

        // Request to ignore battery optimization
        requestBatteryOptimizationExemption()
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val notificationScheduler = NotificationScheduler(this)
            if (!notificationScheduler.canScheduleExactAlarms()) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    exactAlarmPermissionLauncher.launch(intent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to request exact alarm permission: ${e.message}")

                    // Show a user-friendly Toast (optional, for user feedback)
                    Toast.makeText(this, "Failed to request exact alarm permission: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestBatteryOptimizationExemption() {
        val notificationScheduler = NotificationScheduler(this)
        val intent = notificationScheduler.requestIgnoreBatteryOptimization()

        if (intent != null) {
            try {
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to request battery optimization exemption: ${e.message}")

                // Show a user-friendly Toast (optional, for user feedback)
                Toast.makeText(this, "Failed to request battery optimization exemption: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}

@Composable
fun AttendanceScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notificationScheduler = remember { NotificationScheduler(context) }
    var isNotificationEnabled by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var currentSchedules by remember { mutableStateOf(notificationScheduler.getSchedules()) }

    // Load initial state
    LaunchedEffect(Unit) {
        isNotificationEnabled = notificationScheduler.isNotificationEnabled()
        currentSchedules = notificationScheduler.getSchedules()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Attendance Notification",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Current Schedule",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(
                        onClick = { showEditDialog = true }
                    ) {
                        Text("Edit Times")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Monday-Thursday Schedule
                Text(
                    text = "Monday - Thursday:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                currentSchedules.weekdaySchedules.forEach { schedule ->
                    Text(
                        text = "  • ${schedule.time} - ${schedule.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Friday Schedule
                Text(
                    text = "Friday:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                currentSchedules.fridaySchedules.forEach { schedule ->
                    Text(
                        text = "  • ${schedule.time} - ${schedule.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable Notifications",
                style = MaterialTheme.typography.titleMedium
            )

            Switch(
                checked = isNotificationEnabled,
                onCheckedChange = { enabled ->
                    isNotificationEnabled = enabled
                    if (enabled) {
                        notificationScheduler.scheduleAllNotifications()
                    } else {
                        notificationScheduler.cancelAllNotifications()
                    }
                }
            )
        }

        if (isNotificationEnabled) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = "✓ Notifications are scheduled and will work even when the app is closed",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }

    // Edit Schedule Dialog
    if (showEditDialog) {
        EditScheduleDialog(
            currentSchedules = currentSchedules,
            onDismiss = { showEditDialog = false },
            onSave = { newSchedules ->
                currentSchedules = newSchedules
                notificationScheduler.saveSchedules(newSchedules)
                if (isNotificationEnabled) {
                    notificationScheduler.cancelAllNotifications()
                    notificationScheduler.scheduleAllNotifications()
                }
                showEditDialog = false
            }
        )
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun EditScheduleDialog(
    currentSchedules: NotificationSchedules,
    onDismiss: () -> Unit,
    onSave: (NotificationSchedules) -> Unit
) {
    // Fix: Use regular remember with mutableStateOf, not by remember
    var weekdaySchedules by remember { mutableStateOf(currentSchedules.weekdaySchedules) }
    var fridaySchedules by remember { mutableStateOf(currentSchedules.fridaySchedules) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Schedule Times")
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                item {
                    Text(
                        text = "Monday - Thursday",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                itemsIndexed(weekdaySchedules) { index, schedule ->
                    TimePickerRow(
                        label = schedule.message,
                        time = schedule.time,
                        onTimeChange = { newTime ->
                            // Fix: Create new list instead of modifying existing one
                            weekdaySchedules = weekdaySchedules.mapIndexed { i, item ->
                                if (i == index) {
                                    ScheduleItem(newTime, item.message)
                                } else {
                                    item
                                }
                            }
                        }
                    )
                }

                item {
                    Text(
                        text = "Friday",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }

                itemsIndexed(fridaySchedules) { index, schedule ->
                    TimePickerRow(
                        label = schedule.message,
                        time = schedule.time,
                        onTimeChange = { newTime ->
                            // Fix: Create new list instead of modifying existing one
                            fridaySchedules = fridaySchedules.mapIndexed { i, item ->
                                if (i == index) {
                                    ScheduleItem(newTime, item.message)
                                } else {
                                    item
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        NotificationSchedules(
                            weekdaySchedules = weekdaySchedules,
                            fridaySchedules = fridaySchedules
                        )
                    )
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TimePickerRow(
    label: String,
    time: String,
    onTimeChange: (String) -> Unit
) {
    var showTimePicker by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        OutlinedButton(
            onClick = { showTimePicker = true },
            modifier = Modifier.width(100.dp)
        ) {
            Text(time)
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            currentTime = time,
            onTimeSelected = { newTime ->
                onTimeChange(newTime)
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false }
        )
    }
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    currentTime: String,
    onTimeSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val timeParts = currentTime.split(":")
    val initialHour = timeParts[0].toIntOrNull() ?: 7
    val initialMinute = timeParts[1].toIntOrNull() ?: 0

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Select Time")
        },
        text = {
            TimePicker(
                state = timePickerState,
                modifier = Modifier.padding(16.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val hour = String.format("%02d", timePickerState.hour)
                    val minute = String.format("%02d", timePickerState.minute)
                    onTimeSelected("$hour:$minute")
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}