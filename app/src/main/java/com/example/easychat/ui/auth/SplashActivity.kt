package com.example.easychat.ui.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.easychat.ui.compose.theme.EasyChatTheme

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val notificationUserId = intent.extras?.getString("userId")
        setContent {
            EasyChatTheme {
                SplashScreen(notificationUserId = notificationUserId)
            }
        }
    }
}