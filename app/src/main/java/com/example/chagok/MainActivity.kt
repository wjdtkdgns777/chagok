package com.example.chagok

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.example.chagok.ui.ChagokApp
import com.example.chagok.ui.ChagokTheme

class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { Drive.watchLocation() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        if (Store.settings.consented) begin()

        setContent {
            ChagokTheme(still = Drive.driving) {
                ChagokApp(
                    driving = Drive.driving,
                    onAgree = {
                        Store.update(Store.settings.copy(consented = true))
                        begin()
                    },
                    onLater = { moveTaskToBack(true) },
                )
            }
        }
    }

    private fun begin() {
        DriveService.start(this)
        val missing = listOf(Drive.LOCATION_PERMISSION, Manifest.permission.POST_NOTIFICATIONS).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
}
