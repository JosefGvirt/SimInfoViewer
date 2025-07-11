package com.jossi.siminfoviewer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.jossi.siminfoviewer.ui.theme.SimInfoViewerTheme
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SimInfoViewerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SimInfoScreen()
                }
            }
        }
    }
}

@Composable
fun SimInfoScreen() {
    val context = LocalContext.current
    val androidVersion = Build.VERSION.SDK_INT
    val androidVersionName = Build.VERSION.RELEASE
    var phoneNumber by remember { mutableStateOf("Requesting permission...") }
    var manualPhoneNumber by remember { mutableStateOf("") }
    var simInfo by remember { mutableStateOf("") }
    var extraInfo by remember { mutableStateOf(listOf<String>()) }
    var permissionRequested by remember { mutableStateOf(false) }
    var errorLog by remember { mutableStateOf(listOf<String>()) }
    var permissionLog by remember { mutableStateOf(listOf<String>()) }
    var versionMessage by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    }

    fun checkPermissions(): List<String> {
        return requiredPermissions.map { perm ->
            val granted = ActivityCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            "${perm.substringAfterLast('.')} granted: $granted"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = requiredPermissions.all { permissions[it] == true }
        permissionLog = checkPermissions()
        if (granted) {
            val result = getSimAndPhoneInfoWithDebug(context, androidVersion)
            phoneNumber = result.first
            simInfo = result.second
            extraInfo = result.third
            errorLog = result.fourth
            showManualEntry = phoneNumber == "Unavailable"
        } else {
            phoneNumber = "Permission denied"
            simInfo = "Cannot access SIM info without permission."
            extraInfo = emptyList()
            errorLog = listOf("Permission denied for one or more required permissions.")
            showManualEntry = true
        }
    }

    LaunchedEffect(Unit) {
        permissionLog = checkPermissions()
        if (androidVersion in 29..34) {
            versionMessage = "On Android 10–14, access to phone numbers, SIM serial, and device IDs is restricted by the OS for privacy reasons. Even with all permissions, these fields are usually unavailable."
        } else if (androidVersion >= 35) {
            versionMessage = "On Android 15+, access to phone numbers and SIM info may still be limited by your carrier or device, even with all permissions."
        } else {
            versionMessage = "On this Android version, some SIM/device info may be available, but privacy restrictions may still apply."
        }
        val allGranted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val result = getSimAndPhoneInfoWithDebug(context, androidVersion)
            phoneNumber = result.first
            simInfo = result.second
            extraInfo = result.third
            errorLog = result.fourth
            showManualEntry = phoneNumber == "Unavailable"
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(requiredPermissions)
        } else {
            showManualEntry = true
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "SIM Info Viewer", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Android Version: $androidVersion ($androidVersionName)", style = MaterialTheme.typography.bodySmall)
        if (versionMessage.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = versionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Phone Number:")
        Spacer(modifier = Modifier.height(8.dp))
        if (phoneNumber == "Unavailable" && showManualEntry) {
            OutlinedTextField(
                value = manualPhoneNumber,
                onValueChange = { manualPhoneNumber = it },
                label = { Text("Enter your phone number") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (manualPhoneNumber.isNotBlank()) {
                Text(text = "(Manually entered)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        } else {
            Text(text = if (phoneNumber == "Unavailable") "Unavailable (May be restricted by OS or carrier)" else phoneNumber)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Other SIM Info:")
        Spacer(modifier = Modifier.height(8.dp))
        simInfo.split('\n').forEach { line ->
            if (line.contains(": ")) {
                val (label, value) = line.split(": ", limit = 2)
                Text(text = "$label: ${if (value.isBlank()) "Unavailable (May be restricted by OS or carrier)" else value}", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(text = line, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (extraInfo.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Extra Device/SIM Info:")
            extraInfo.forEach { n ->
                if (n.contains(": ")) {
                    val (label, value) = n.split(": ", limit = 2)
                    Text(text = "$label: ${if (value.isBlank() || value.contains("Error") || value.contains("Restricted")) "Unavailable (May be restricted by OS or app permissions)" else value}", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(text = n, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (permissionLog.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Permissions:", style = MaterialTheme.typography.labelSmall)
            permissionLog.forEach { log ->
                Text(text = log, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (errorLog.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Debug/Error Log:", style = MaterialTheme.typography.labelSmall)
            errorLog.forEach { err ->
                Text(text = err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Why info may be missing: On modern Android, access to phone number, SIM serial, IMEI, and IMSI is often restricted for privacy. Carriers may not store the number on the SIM. If info is unavailable, this is normal.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

fun getSimAndPhoneInfoWithDebug(context: android.content.Context, androidVersion: Int): Quadruple<String, String, List<String>, List<String>> {
    val tm = context.getSystemService(TelephonyManager::class.java)
    val sm = context.getSystemService(SubscriptionManager::class.java)
    var number: String? = null
    var simInfo = StringBuilder()
    val extraInfo = mutableListOf<String>()
    val debug = mutableListOf<String>()
    try {
        try {
            number = tm.line1Number
            debug.add("TelephonyManager.line1Number: ${number ?: "null"}")
        } catch (e: Exception) {
            debug.add("TelephonyManager.line1Number: Exception: ${e.message}")
        }
        if (number.isNullOrBlank()) {
            number = "Unavailable"
        }
        try {
            if (androidVersion >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val activeSubs = sm.activeSubscriptionInfoList
                if (!activeSubs.isNullOrEmpty()) {
                    for (sub in activeSubs) {
                        simInfo.append("Carrier: ").append(sub.carrierName).append("\n")
                        simInfo.append("Display Name: ").append(sub.displayName).append("\n")
                        simInfo.append("Number: ").append(sub.number).append("\n")
                        simInfo.append("SIM Slot: ").append(sub.simSlotIndex).append("\n")
                        simInfo.append("Country: ").append(sub.countryIso).append("\n\n")
                        debug.add("SubscriptionInfo.number: ${sub.number ?: "null"}")
                    }
                } else {
                    simInfo.append("No active SIM subscriptions found.")
                    debug.add("No active SIM subscriptions found.")
                }
            } else {
                simInfo.append("SIM info not available on this Android version.")
                debug.add("SIM info not available on this Android version.")
            }
        } catch (e: Exception) {
            simInfo.append("Error: ${e.message}")
            debug.add("SubscriptionManager.activeSubscriptionInfoList: Exception: ${e.message}")
        }
        // Extra info with try/catch
        try {
            extraInfo.add("SIM Serial: ${try { tm.simSerialNumber ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) { extraInfo.add("SIM Serial: Error: ${e.message}") }
        try {
            extraInfo.add("SIM Operator: ${try { tm.simOperatorName ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) { extraInfo.add("SIM Operator: Error: ${e.message}") }
        try {
            extraInfo.add("SIM Country: ${try { tm.simCountryIso ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) { extraInfo.add("SIM Country: Error: ${e.message}") }
        try {
            extraInfo.add("SIM State: ${try { tm.simState.toString() } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) { extraInfo.add("SIM State: Error: ${e.message}") }
        try {
            extraInfo.add("Device ID (IMEI): ${try { tm.deviceId ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) { extraInfo.add("Device ID (IMEI): Error: ${e.message}") }
        try {
            extraInfo.add("Subscriber ID (IMSI): ${try { tm.subscriberId ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) { extraInfo.add("Subscriber ID (IMSI): Error: ${e.message}") }
        try {
            val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
            extraInfo.add("Android ID: $androidId")
        } catch (e: Exception) { extraInfo.add("Android ID: Error: ${e.message}") }
        extraInfo.add("Device Model: ${android.os.Build.MODEL}")
        extraInfo.add("Device Manufacturer: ${android.os.Build.MANUFACTURER}")
    } catch (e: Exception) {
        debug.add("Exception: ${e.message}")
    }
    return Quadruple(number ?: "Unavailable", simInfo.toString().trim(), extraInfo, debug)
}

class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

fun getAllPossiblePhoneNumbers(context: android.content.Context): List<String> {
    val numbers = mutableListOf<String>()
    val tm = context.getSystemService(TelephonyManager::class.java)
    val sm = context.getSystemService(SubscriptionManager::class.java)
    // Try TelephonyManager.line1Number
    tm.line1Number?.let { if (it.isNotBlank()) numbers.add("TelephonyManager: $it") }
    // Try SubscriptionManager/SubscriptionInfo
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
        sm.activeSubscriptionInfoList?.forEach { sub ->
            sub.number?.let { if (it.isNotBlank()) numbers.add("SubscriptionInfo: $it") }
        }
    }
    return numbers
}

fun getAllExtraSimDeviceInfo(context: android.content.Context): List<String> {
    val info = mutableListOf<String>()
    val tm = context.getSystemService(TelephonyManager::class.java)
    try {
        info.add("SIM Serial: ${tm.simSerialNumber ?: "Unavailable"}")
        info.add("SIM Operator: ${tm.simOperatorName ?: "Unavailable"}")
        info.add("SIM Country: ${tm.simCountryIso ?: "Unavailable"}")
        info.add("SIM State: ${tm.simState}")
        info.add("Device ID (IMEI): ${try { tm.deviceId ?: "Unavailable" } catch (e: Exception) { "Restricted" }}")
        info.add("Subscriber ID (IMSI): ${try { tm.subscriberId ?: "Unavailable" } catch (e: Exception) { "Restricted" }}")
    } catch (e: Exception) {
        info.add("Error: ${e.message}")
    }
    // Android ID
    try {
        val androidId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        info.add("Android ID: $androidId")
    } catch (e: Exception) {
        info.add("Android ID: Error: ${e.message}")
    }
    // Device model
    info.add("Device Model: ${android.os.Build.MODEL}")
    info.add("Device Manufacturer: ${android.os.Build.MANUFACTURER}")
    return info
}