package com.gamebox.builder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gamebox.builder.ui.GameBoxApp
import com.gamebox.builder.ui.theme.GameBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GameBoxTheme {
                GameBoxApp()
            }
        }
    }
}
