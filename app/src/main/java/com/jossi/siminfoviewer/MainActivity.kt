package com.jossi.siminfoviewer

import android.Manifest
import android.content.Intent
import android.content.IntentSender
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
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.HintRequest
import com.google.android.gms.auth.api.credentials.Credentials
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

class MainActivity : ComponentActivity() {
    private var phoneNumberCallback: ((String) -> Unit)? = null
    
    companion object {
        private const val PHONE_NUMBER_RC = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            SimInfoViewerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SimInfoScreen(
                        onRequestGooglePhoneNumber = { callback ->
                            requestPhoneNumberFromGoogle(callback)
                        }
                    )
                }
            }
        }
    }
    
    private fun requestPhoneNumberFromGoogle(callback: (String) -> Unit) {
        phoneNumberCallback = callback
        val hintRequest = HintRequest.Builder()
            .setPhoneNumberIdentifierSupported(true)
            .build()
        
        try {
            val intent = Credentials.getClient(this).getHintPickerIntent(hintRequest)
            startIntentSenderForResult(intent.intentSender, PHONE_NUMBER_RC, null, 0, 0, 0)
        } catch (e: IntentSender.SendIntentException) {
            callback("Error: Could not start Google phone picker")
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PHONE_NUMBER_RC) {
            if (resultCode == RESULT_OK && data != null) {
                val credential = data.getParcelableExtra<Credential>(Credential.EXTRA_KEY)
                if (credential != null && phoneNumberCallback != null) {
                    phoneNumberCallback?.invoke(credential.id ?: "No number selected")
                }
            } else {
                phoneNumberCallback?.invoke("User cancelled Google phone picker")
            }
            phoneNumberCallback = null
        }
    }
}

@Composable
fun SimInfoScreen(onRequestGooglePhoneNumber: ((String) -> Unit) -> Unit) {
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
    
    // Google fallback state
    var showGoogleFallback by remember { mutableStateOf(false) }
    var googlePhoneNumber by remember { mutableStateOf("") }
    var simMethodFailed by remember { mutableStateOf(false) }

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
                        // Silent error handling for individual SIM
                    }
                }
            }
        } catch (e: Exception) {
            // Silent error handling for SubscriptionManager
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
            
            // Check if SIM method failed and show Google fallback
            if (numbers.isEmpty()) {
                simMethodFailed = true
                showGoogleFallback = true
            }
            
            checkWifiConnection(context)
        } else {
            phoneNumbers = emptyList()
            carrierNames = emptyList()
            simSlots = emptyList()
            countryCodes = emptyList()
            simMethodFailed = true
            showGoogleFallback = true
            
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
            
            // Check if SIM method failed and show Google fallback
            if (numbers.isEmpty()) {
                simMethodFailed = true
                showGoogleFallback = true
            }
            
            checkWifiConnection(context)
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(requiredPermissions)
        } else if (wifiPermissionGranted) {
            checkWifiConnection(context)
        }
    }

    // Replace Column with a scrollable Box and Column
    Box(modifier = Modifier.fillMaxSize()) {
        // Main scrollable content
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 200.dp), // Add bottom padding for bottom section
            verticalArrangement = Arrangement.Top
        ) {
            Text(text = "SIM Info Viewer", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Android Version: $androidVersion ($androidVersionName)", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(text = "Phone Number(s):", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            if (simSlots.isNotEmpty()) {
                simSlots.forEachIndexed { idx, slot ->
                    Text(text = "SIM Slot $slot:", style = MaterialTheme.typography.bodyMedium)
                    val number = phoneNumbers.getOrNull(idx)
                    val carrier = carrierNames.getOrNull(idx) ?: "Unknown"
                    val country = countryCodes.getOrNull(idx) ?: "Unknown"
                    if (!number.isNullOrBlank()) {
                        Text(text = "Phone Number: $number", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text(text = "Phone Number: Not available", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(text = "Carrier: $carrier", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Country: $country", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            } else {
                Text(text = "No SIM info available", style = MaterialTheme.typography.bodySmall)
            }
            
            // Google fallback section
            if (showGoogleFallback) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Alternative Method:", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Get phone number from Google account", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (googlePhoneNumber.isNotEmpty()) {
                    Text(text = "Google: $googlePhoneNumber", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else {
                    // Restore Google account picker button
                    Button(
                        onClick = { 
                            onRequestGooglePhoneNumber { number ->
                                googlePhoneNumber = number
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Get Phone Number from Google")
                    }
                    // Prompt and avatar call buttons for Martha, Lamis, Anna
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Try calling one of the avatars to retrieve number",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1976D2) // Blue
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        AvatarCallButton(name = "Martha", number = "+972546763889", context = context)
                        AvatarCallButton(name = "Lamis", number = "+972546763889", context = context)
                        AvatarCallButton(name = "Anna", number = "+972546763889", context = context)
                    }
                }
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
                Text(text = "⚠️ Connected to ADU network", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { openWifiSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)) // Orange
                ) {
                    Text("Open WiFi Settings to Forget ADU")
                }
            } else if (currentWifiSSID != "Not connected" && currentWifiSSID != "Error: " && currentWifiSSID.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "✅ Not connected to ADU", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            // Cellular network alert logic
            val nonPelephoneCarriers = carrierNames.filter { it.isNotBlank() && !it.equals("Pelephone", ignoreCase = true) }
            if (nonPelephoneCarriers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "⚠️ Connected to external cellular network: ${nonPelephoneCarriers.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        // Open cellular data settings
                        val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)) // Orange
                ) {
                    Text("Open Cellular Settings to Disconnect Data")
                }
            } else if (carrierNames.any { it.equals("Pelephone", ignoreCase = true) }) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ Not connected to external network",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            if (!wifiPermissionGranted) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Location permission needed to check WiFi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        // Bottom action section: only if phone number is not found
        if (phoneNumbers.isEmpty() && googlePhoneNumber.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { 
                        onRequestGooglePhoneNumber { number ->
                            googlePhoneNumber = number
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                ) {
                    Text("Get Phone Number from Google")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Try calling one of the avatars to retrieve number",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF1976D2) // Blue
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarCallButton(name = "Martha", number = "+972546763889", context = context)
                    Spacer(modifier = Modifier.height(8.dp))
                    AvatarCallButton(name = "Lamis", number = "+972546763889", context = context)
                    Spacer(modifier = Modifier.height(8.dp))
                    AvatarCallButton(name = "Anna", number = "+972546763889", context = context)
                }
            }
        }
        // Animated footer always at the very bottom
        AnimatedFooter(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun AnimatedFooter(modifier: Modifier = Modifier) {
    // Animate RGB color
    val infiniteTransition = rememberInfiniteTransition(label = "footerColor")
    val red by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 255f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "red"
    )
    val green by infiniteTransition.animateFloat(
        initialValue = 255f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "green"
    )
    val blue by infiniteTransition.animateFloat(
        initialValue = 128f,
        targetValue = 255f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "blue"
    )
    val animatedColor = Color(red.toInt(), green.toInt(), blue.toInt())
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = "created by Yossi The Peeeeps 😎",
            color = animatedColor,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 32.dp, bottom = 8.dp)
        )
    }
}

data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun AvatarCallButton(name: String, number: String, context: android.content.Context) {
    if (name.isBlank()) return // Do not render button if name is empty
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$number")
            context.startActivity(intent)
        },
        modifier = Modifier.padding(horizontal = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90CAF9)) // Light blue
    ) {
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(name)
    }
}