package com.example.easychat.ui.auth

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil

@Composable
fun SplashScreen(
    notificationUserId: String?,
    viewModel: SplashViewModel = viewModel()
) {
    val context = LocalContext.current
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    // Dispara a resolução de destino uma única vez
    LaunchedEffect(Unit) {
        val delay = if (notificationUserId != null) 0L else 1000L
        Handler(Looper.getMainLooper()).postDelayed({
            viewModel.resolveDestination(notificationUserId)
        }, delay)
    }

    // Reage ao destino resolvido — navega imperativamente como o original
    LaunchedEffect(destination) {
        destination ?: return@LaunchedEffect
        when (val dest = destination) {
            is SplashDestination.Main -> {
                context.startActivity(Intent(context, MainActivity::class.java))
                (context as? android.app.Activity)?.finish()
            }
            is SplashDestination.Login -> {
                context.startActivity(Intent(context, LoginPhoneNumberActivity::class.java))
                (context as? android.app.Activity)?.finish()
            }
            is SplashDestination.ChatFromNotification -> {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                })
                context.startActivity(Intent(context, ChatActivity::class.java).apply {
                    AndroidUtil.passUserModelAsIntent(this, dest.user)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                (context as? android.app.Activity)?.finish()
            }
            null -> Unit
        }
        viewModel.clearDestination()
    }

    // UI da splash
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "EasyChat",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator()
        }
    }
}