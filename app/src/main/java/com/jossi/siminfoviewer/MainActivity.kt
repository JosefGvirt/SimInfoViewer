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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.READ_PHONE_STATE] == true &&
                permissions[Manifest.permission.READ_PHONE_NUMBERS] == true

        phoneNumber = if (granted) {
            getPhoneNumber(context)
        } else {
            "Permission denied"
        }
    }

    LaunchedEffect(Unit) {
        val hasState = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val hasNumbers = ActivityCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_NUMBERS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasState && hasNumbers) {
            phoneNumber = getPhoneNumber(context)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_PHONE_NUMBERS
                )
            )
        }
    }

    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = "SIM Info Viewer", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Phone Number:")
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = phoneNumber)
    }
}

fun getPhoneNumber(context: android.content.Context): String {
    return try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        tm.line1Number ?: "Unavailable"
    } catch (e: Exception) {
        "Error: ${e.message}"
    }
}