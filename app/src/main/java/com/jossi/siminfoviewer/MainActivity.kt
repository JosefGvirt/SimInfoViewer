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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.activity.compose.BackHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.SolidColor

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
    var googlePickerMessage by remember { mutableStateOf("") }
    var simMethodFailed by remember { mutableStateOf(false) }
    var showAvatarPage by remember { mutableStateOf(false) }

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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {
        val maxHeight = maxHeight
        val baseHeight = 900.dp // Estimate for all main content + footer
        val scale = (maxHeight / baseHeight).coerceIn(0.7f, 1f)
        val scrollState = rememberScrollState()
        val infiniteTransition = rememberInfiniteTransition()
        val bounce by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 16f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse
            )
        )
        val coroutineScope = rememberCoroutineScope()
        Box {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .verticalScroll(scrollState)
                    .padding(24.dp, 24.dp, 24.dp, 72.dp), // leave space for footer
                verticalArrangement = Arrangement.Top
            ) {
                Text(text = "SIM Info Viewer", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Android Version: $androidVersion ($androidVersionName)", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(text = "Phone Number(s):", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (simSlots.isNotEmpty()) {
                    simSlots.forEachIndexed { idx, slot ->
                        Text(text = "SIM Slot $slot:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        val number = phoneNumbers.getOrNull(idx)
                        val carrier = carrierNames.getOrNull(idx) ?: "Unknown"
                        val country = countryCodes.getOrNull(idx) ?: "Unknown"
                        if (!number.isNullOrBlank()) {
                            Text(text = "Phone Number: $number", fontSize = 12.sp)
                        } else {
                            Text(text = "Phone Number: Not available", fontSize = 12.sp)
                        }
                        Text(text = "Carrier: $carrier", fontSize = 12.sp)
                        Text(text = "Country: $country", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                } else {
                    Text(text = "No SIM info available", fontSize = 12.sp)
                }
                
                // Google fallback section
                if (showGoogleFallback) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Alternative Method:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Get phone number from Google account", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (googlePhoneNumber.isNotEmpty()) {
                        Text(text = "Google: $googlePhoneNumber", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Button(
                            onClick = { 
                                onRequestGooglePhoneNumber { number ->
                                    if (number.startsWith("+") || number.any { it.isDigit() }) {
                                        googlePhoneNumber = number
                                        googlePickerMessage = ""
                                    } else {
                                        googlePickerMessage = "No number selected, try again."
                                        googlePhoneNumber = "" // Ensure button stays visible
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 36.dp),
                        ) {
                            Text("Get Phone Number from Google", fontSize = 12.sp)
                        }
                        if (googlePickerMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = googlePickerMessage,
                                fontSize = 12.sp,
                                color = Color(0xFF1976D2) // Blue
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Call Avatars button
                        Button(
                            onClick = { showAvatarPage = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 36.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90CAF9))
                        ) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Call Avatars", fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Device Info:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Model: $deviceModel", fontSize = 12.sp)
                Text(text = "Manufacturer: $deviceManufacturer", fontSize = 12.sp)
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "WiFi Network:", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Current: $currentWifiSSID", fontSize = 12.sp)
                
                if (isConnectedToADU) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "⚠️ Connected to ADU network", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { openWifiSettings() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)) // Orange
                    ) {
                        Text("Open WiFi Settings to Forget ADU", fontSize = 12.sp)
                    }
                } else if (currentWifiSSID != "Not connected" && currentWifiSSID != "Error: " && currentWifiSSID.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "✅ Not connected to ADU", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }

                // Cellular network alert logic
                val nonPelephoneCarriers = carrierNames.filter { it.isNotBlank() && !it.equals("Pelephone", ignoreCase = true) }
                if (nonPelephoneCarriers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "⚠️ Connected to external cellular network: ${nonPelephoneCarriers.joinToString(", ")}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            // Open cellular data settings
                            val intent = Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
                            context.startActivity(intent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)) // Orange
                    ) {
                        Text("Open Cellular Settings to Disconnect Data", fontSize = 12.sp)
                    }
                } else if (carrierNames.any { it.equals("Pelephone", ignoreCase = true) }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✅ Not connected to external network",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                if (!wifiPermissionGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Location permission needed to check WiFi", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.weight(1f, fill = true))
            }
            // Down arrow indicator (show if not at bottom)
            AnimatedVisibility(
                visible = scrollState.value < scrollState.maxValue,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 80.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = Color(0xFF2196F3), // solid blue
                            shape = CircleShape
                        )
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Scroll down for more",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer(translationY = bounce)
                    )
                }
            }
            // Up arrow indicator (show if not at top)
            AnimatedVisibility(
                visible = scrollState.value > 0,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = Color(0xFF2196F3), // solid blue
                            shape = CircleShape
                        )
                        .clickable {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                            }
                        }
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Scroll up for more",
                        tint = Color.White,
                        modifier = Modifier
                            .size(28.dp)
                            .graphicsLayer(translationY = -bounce)
                    )
                }
            }
        }
        AnimatedFooter(modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAvatarPage) {
        AvatarCallerPage(onBack = { showAvatarPage = false })
        return
    }
}

@Composable
fun AvatarCallerPage(onBack: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onBack() }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F4F8)) // Soft blue-grey background
            .navigationBarsPadding() // Add padding for nav bar
    ) {
        val maxHeight = maxHeight
        val maxWidth = maxWidth
        // Calculate scale factor to fit all content
        val baseHeight = 500.dp // estimated base height for all content
        val scale = (maxHeight / baseHeight).coerceAtMost(1f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .padding(24.dp, 24.dp, 24.dp, 72.dp), // leave space for footer
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Call an Avatar",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.Black
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            AvatarCallButton(name = "Martha", number = "+972546763889", context = context)
            Spacer(modifier = Modifier.height(16.dp))
            AvatarCallButton(name = "Lamis", number = "+972546763889", context = context)
            Spacer(modifier = Modifier.height(16.dp))
            AvatarCallButton(name = "Anna", number = "+972546763889", context = context)
        }
        AnimatedFooter(modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun AvatarCallButton(name: String, number: String, context: android.content.Context) {
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = android.net.Uri.parse("tel:$number")
            context.startActivity(intent)
        },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF90CAF9))
    ) {
        Icon(imageVector = Icons.Default.Person, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(name)
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