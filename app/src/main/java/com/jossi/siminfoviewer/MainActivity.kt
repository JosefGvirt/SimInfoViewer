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
    var phoneNumbers by remember { mutableStateOf(listOf<String>()) }
    var carrierNames by remember { mutableStateOf(listOf<String>()) }
    var simSlots by remember { mutableStateOf(listOf<Int>()) }
    var countryCodes by remember { mutableStateOf(listOf<String>()) }
    var manualPhoneNumber by remember { mutableStateOf("") }
    var showManualEntry by remember { mutableStateOf(false) }
    var extraInfo by remember { mutableStateOf(listOf<String>()) }
    var permissionRequested by remember { mutableStateOf(false) }
    var errorLog by remember { mutableStateOf(listOf<String>()) }
    var permissionLog by remember { mutableStateOf(listOf<String>()) }
    var versionMessage by remember { mutableStateOf("") }

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

    fun getAllSimInfo(context: android.content.Context): Quadruple<List<String>, List<String>, List<Int>, List<String>> {
        val numbers = mutableListOf<String>()
        val carriers = mutableListOf<String>()
        val slots = mutableListOf<Int>()
        val countries = mutableListOf<String>()
        try {
            val sm = context.getSystemService(SubscriptionManager::class.java)
            val infoList = sm?.activeSubscriptionInfoList
            if (!infoList.isNullOrEmpty()) {
                for (sub in infoList) {
                    try {
                        val number = sub.number ?: ""
                        val carrier = sub.carrierName?.toString() ?: ""
                        val slot = sub.simSlotIndex
                        val country = sub.countryIso ?: ""
                        if (number.isNotBlank()) numbers.add(number)
                        carriers.add(carrier)
                        slots.add(slot)
                        countries.add(country)
                    } catch (e: Exception) {
                        errorLog = errorLog + "SubscriptionInfo error: ${e.message}"
                    }
                }
            }
        } catch (e: Exception) {
            errorLog = errorLog + "SubscriptionManager error: ${e.message}"
        }
        return Quadruple(numbers, carriers, slots, countries)
    }

    fun getExtraSimDeviceInfo(context: android.content.Context): List<String> {
        val info = mutableListOf<String>()
        val tm = context.getSystemService(TelephonyManager::class.java)
        try {
            info.add("Android ID: ${try { android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) } catch (e: Exception) { "Error: ${e.message}" }}")
            info.add("Device Model: ${android.os.Build.MODEL}")
            info.add("Device Manufacturer: ${android.os.Build.MANUFACTURER}")
            info.add("SIM Serial: ${try { tm.simSerialNumber ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
            info.add("Subscriber ID (IMSI): ${try { tm.subscriberId ?: "Unavailable" } catch (e: Exception) { "Error: ${e.message}" }}")
        } catch (e: Exception) {
            info.add("Extra info error: ${e.message}")
        }
        return info
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = requiredPermissions.all { permissions[it] == true }
        permissionLog = checkPermissions()
        if (granted) {
            val (numbers, carriers, slots, countries) = getAllSimInfo(context)
            phoneNumbers = numbers
            carrierNames = carriers
            simSlots = slots
            countryCodes = countries
            extraInfo = getExtraSimDeviceInfo(context)
            showManualEntry = phoneNumbers.isEmpty()
        } else {
            phoneNumbers = emptyList()
            carrierNames = emptyList()
            simSlots = emptyList()
            countryCodes = emptyList()
            extraInfo = getExtraSimDeviceInfo(context)
            showManualEntry = true
        }
    }

    LaunchedEffect(Unit) {
        permissionLog = checkPermissions()
        if (androidVersion in 29..34) {
            versionMessage = "Due to OS privacy restrictions, phone number and SIM serial access may be blocked even with permissions."
        } else if (androidVersion >= 35) {
            versionMessage = "On Android 15+, access to phone numbers and SIM info may still be limited by your carrier or device, even with all permissions."
        } else {
            versionMessage = "On this Android version, some SIM/device info may be available, but privacy restrictions may still apply."
        }
        val allGranted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val (numbers, carriers, slots, countries) = getAllSimInfo(context)
            phoneNumbers = numbers
            carrierNames = carriers
            simSlots = slots
            countryCodes = countries
            extraInfo = getExtraSimDeviceInfo(context)
            showManualEntry = phoneNumbers.isEmpty()
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
        Text(text = "Phone Number(s):")
        Spacer(modifier = Modifier.height(8.dp))
        if (phoneNumbers.isNotEmpty()) {
            phoneNumbers.forEachIndexed { idx, number ->
                Text(text = "SIM Slot ${simSlots.getOrNull(idx) ?: "?"}: ${number}", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Carrier: ${carrierNames.getOrNull(idx) ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Country: ${countryCodes.getOrNull(idx) ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else if (showManualEntry) {
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
            Text(text = "No phone number available.", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Extra Device/SIM Info:", style = MaterialTheme.typography.labelSmall)
        extraInfo.forEach { n ->
            Text(text = n, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Permissions:", style = MaterialTheme.typography.labelSmall)
        permissionLog.forEach { log ->
            Text(text = log, style = MaterialTheme.typography.bodySmall)
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

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

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