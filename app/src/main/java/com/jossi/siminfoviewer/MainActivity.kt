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
    var phoneNumber by remember { mutableStateOf("Requesting permission...") }
    var simInfo by remember { mutableStateOf("") }
    var allNumbers by remember { mutableStateOf(listOf<String>()) }
    var extraInfo by remember { mutableStateOf(listOf<String>()) }
    var permissionRequested by remember { mutableStateOf(false) }
    var errorLog by remember { mutableStateOf(listOf<String>()) }

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.READ_SMS
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = requiredPermissions.all { permissions[it] == true }
        if (granted) {
            val (number, info, errors) = getPhoneNumberAndSimInfoWithErrors(context)
            phoneNumber = number
            simInfo = info
            allNumbers = getAllPossiblePhoneNumbers(context)
            extraInfo = getAllExtraSimDeviceInfo(context)
            errorLog = errors
        } else {
            phoneNumber = "Permission denied"
            simInfo = "Cannot access SIM info without permission."
            allNumbers = emptyList()
            extraInfo = emptyList()
            errorLog = listOf("Permission denied for one or more required permissions.")
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val (number, info, errors) = getPhoneNumberAndSimInfoWithErrors(context)
            phoneNumber = number
            simInfo = info
            allNumbers = getAllPossiblePhoneNumbers(context)
            extraInfo = getAllExtraSimDeviceInfo(context)
            errorLog = errors
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "SIM Info Viewer", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Android Version: $androidVersion (${Build.VERSION.RELEASE})", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Phone Number:")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = if (phoneNumber == "Unavailable") "Unavailable (May be restricted by OS or carrier)" else phoneNumber)
        if (allNumbers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "All Possible Phone Numbers:")
            allNumbers.forEach { n ->
                Text(text = n, style = MaterialTheme.typography.bodySmall)
            }
        } else if (phoneNumber == "Unavailable") {
            Text(text = "No phone number available from any API.", style = MaterialTheme.typography.bodySmall)
        }
        if (simInfo.isNotBlank()) {
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

fun getPhoneNumberAndSimInfoWithErrors(context: android.content.Context): Triple<String, String, List<String>> {
    val tm = context.getSystemService(TelephonyManager::class.java)
    val sm = context.getSystemService(SubscriptionManager::class.java)
    var number: String? = null
    var simInfo = StringBuilder()
    val errors = mutableListOf<String>()
    try {
        number = tm.line1Number
        if (number.isNullOrBlank()) {
            errors.add("TelephonyManager.line1Number is null or blank.")
            number = "Unavailable"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val activeSubs = sm.activeSubscriptionInfoList
            if (!activeSubs.isNullOrEmpty()) {
                for (sub in activeSubs) {
                    simInfo.append("Carrier: ").append(sub.carrierName).append("\n")
                    simInfo.append("Display Name: ").append(sub.displayName).append("\n")
                    simInfo.append("Number: ").append(sub.number).append("\n")
                    simInfo.append("SIM Slot: ").append(sub.simSlotIndex).append("\n")
                    simInfo.append("Country: ").append(sub.countryIso).append("\n\n")
                }
            } else {
                simInfo.append("No active SIM subscriptions found.")
                errors.add("No active SIM subscriptions found.")
            }
        } else {
            simInfo.append("SIM info not available on this Android version.")
            errors.add("SIM info not available on this Android version.")
        }
    } catch (e: Exception) {
        number = "Unavailable"
        simInfo.append("Error: ${e.message}")
        errors.add("Exception: ${e.message}")
    }
    return Triple(number ?: "Unavailable", simInfo.toString().trim(), errors)
}

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