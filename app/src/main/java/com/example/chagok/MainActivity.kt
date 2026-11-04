package com.example.chagok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.example.chagok.ui.ChagokTheme
import com.example.chagok.ui.LocalPalette
import com.example.chagok.ui.Text
import com.example.chagok.ui.Type

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            ChagokTheme(still = false) {
                val palette = LocalPalette.current
                Column(Modifier.fillMaxSize().background(palette.background), Arrangement.Center, Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.app_name), Type.headline, palette.text)
                    Text(stringResource(R.string.tagline), Type.body, palette.textSub)
                }
            }
        }
    }
}
