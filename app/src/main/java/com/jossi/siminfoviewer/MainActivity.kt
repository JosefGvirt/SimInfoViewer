package com.jossi.siminfoviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
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
    var deviceModel by remember { mutableStateOf("") }
    var deviceManufacturer by remember { mutableStateOf("") }
    var permissionRequested by remember { mutableStateOf(false) }
    
    // WiFi related state
    var currentWifiSSID by remember { mutableStateOf("") }
    var isConnectedToADU by remember { mutableStateOf(false) }
    var wifiPermissionGranted by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
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
                        // Silent error handling
                    }
                }
            }
        } catch (e: Exception) {
            // Silent error handling
        }
        return Quadruple(numbers, carriers, slots, countries)
    }

    fun checkWifiConnection(context: android.content.Context) {
        try {
            val wifiManager = context.getSystemService(WifiManager::class.java)
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid
            
            if (ssid != null && ssid.isNotBlank()) {
                // Remove quotes from SSID
                currentWifiSSID = ssid.removeSurrounding("\"")
                isConnectedToADU = currentWifiSSID.equals("ADU", ignoreCase = true)
            } else {
                currentWifiSSID = "Not connected"
                isConnectedToADU = false
            }
        } catch (e: Exception) {
            currentWifiSSID = "Error: ${e.message}"
            isConnectedToADU = false
        }
    }

    fun openWifiSettings() {
        val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
        context.startActivity(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = requiredPermissions.all { permissions[it] == true }
        wifiPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (granted) {
            val (numbers, carriers, slots, countries) = getAllSimInfo(context)
            phoneNumbers = numbers
            carrierNames = carriers
            simSlots = slots
            countryCodes = countries
            checkWifiConnection(context)
        } else {
            phoneNumbers = emptyList()
            carrierNames = emptyList()
            simSlots = emptyList()
            countryCodes = emptyList()
            if (wifiPermissionGranted) {
                checkWifiConnection(context)
            }
        }
    }

    LaunchedEffect(Unit) {
        deviceModel = android.os.Build.MODEL
        deviceManufacturer = android.os.Build.MANUFACTURER
        
        val allGranted = requiredPermissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        wifiPermissionGranted = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        if (allGranted) {
            val (numbers, carriers, slots, countries) = getAllSimInfo(context)
            phoneNumbers = numbers
            carrierNames = carriers
            simSlots = slots
            countryCodes = countries
            checkWifiConnection(context)
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(requiredPermissions)
        } else if (wifiPermissionGranted) {
            checkWifiConnection(context)
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "SIM Info Viewer", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Android Version: $androidVersion ($androidVersionName)", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(text = "Phone Number(s):", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        if (phoneNumbers.isNotEmpty()) {
            phoneNumbers.forEachIndexed { idx, number ->
                Text(text = "SIM Slot ${simSlots.getOrNull(idx) ?: "?"}: $number", style = MaterialTheme.typography.bodyMedium)
                Text(text = "Carrier: ${carrierNames.getOrNull(idx) ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                Text(text = "Country: ${countryCodes.getOrNull(idx) ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            Text(text = "No phone number available", style = MaterialTheme.typography.bodySmall)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Device Info:", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Model: $deviceModel", style = MaterialTheme.typography.bodySmall)
        Text(text = "Manufacturer: $deviceManufacturer", style = MaterialTheme.typography.bodySmall)
        
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "WiFi Network:", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Current: $currentWifiSSID", style = MaterialTheme.typography.bodySmall)
        
        if (isConnectedToADU) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "⚠️ Connected to ADU network", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { openWifiSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open WiFi Settings to Forget ADU")
            }
        } else if (currentWifiSSID != "Not connected" && currentWifiSSID != "Error: " && currentWifiSSID.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "✅ Not connected to ADU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        
        if (!wifiPermissionGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Location permission needed to check WiFi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)