package com.example.easychat.ui.search

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.easychat.ui.compose.theme.EasyChatTheme

class SearchUserActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyChatTheme {
                SearchScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }
}
