package com.edgepass

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.edgepass.ui.EdgePassApp
import com.edgepass.ui.theme.EdgePassTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EdgePassTheme {
                EdgePassApp()
            }
        }
    }
}
