package com.example.easychat.ui.auth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.easychat.ui.compose.theme.EasyChatTheme

class LoginPhoneNumberActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyChatTheme {
                LoginPhoneScreen()
            }
        }
    }
}