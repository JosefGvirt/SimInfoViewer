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
    var phoneNumber by remember { mutableStateOf("Requesting permission...") }
    var simInfo by remember { mutableStateOf("") }
    var allNumbers by remember { mutableStateOf(listOf<String>()) }
    var permissionRequested by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.READ_PHONE_NUMBERS
            )
        } else {
            arrayOf(Manifest.permission.READ_PHONE_STATE)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = requiredPermissions.all { permissions[it] == true }
        if (granted) {
            val (number, info) = getPhoneNumberAndSimInfo(context)
            phoneNumber = number
            simInfo = info
            allNumbers = getAllPossiblePhoneNumbers(context)
        } else {
            phoneNumber = "Permission denied"
            simInfo = "Cannot access SIM info without permission."
            allNumbers = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        val allGranted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            val (number, info) = getPhoneNumberAndSimInfo(context)
            phoneNumber = number
            simInfo = info
            allNumbers = getAllPossiblePhoneNumbers(context)
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(requiredPermissions)
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "SIM Info Viewer", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Phone Number:")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = phoneNumber)
        if (allNumbers.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "All Possible Phone Numbers:")
            allNumbers.forEach { n ->
                Text(text = n, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (simInfo.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Other SIM Info:")
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = simInfo)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Note: Many carriers and devices do not provide the phone number to apps for privacy reasons. If your number is not shown, this is likely the cause.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

fun getPhoneNumberAndSimInfo(context: android.content.Context): Pair<String, String> {
    val tm = context.getSystemService(TelephonyManager::class.java)
    val sm = context.getSystemService(SubscriptionManager::class.java)
    var number: String? = null
    var simInfo = StringBuilder()
    try {
        // Try to get phone number
        number = tm.line1Number
        if (number.isNullOrBlank()) {
            number = "Unavailable"
        }
        // Fallback: show carrier and SIM info
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
            }
        } else {
            simInfo.append("SIM info not available on this Android version.")
        }
    } catch (e: Exception) {
        number = "Error: ${e.message}"
        simInfo.append("Error: ${e.message}")
    }
    return Pair(number ?: "Unavailable", simInfo.toString().trim())
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