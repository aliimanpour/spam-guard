package com.smsspam.detector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smsspam.detector.ui.AppRoot
import com.smsspam.detector.ui.theme.SpamGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpamGuardTheme {
                AppRoot()
            }
        }
    }
}
