package com.example.easychat.ui.main

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.easychat.ui.compose.theme.EasyChatTheme
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    val viewModel: MainViewModel by viewModels()

    // ─── Launcher de imagem: registrado na Activity para evitar
    //     IllegalStateException com o ImagePicker (igual ao original) ─────────
    private var onImagePickedCallback: ((Uri) -> Unit)? = null

    val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            onImagePickedCallback?.invoke(uri)
        }
        onImagePickedCallback = null
    }

    fun launchImagePicker(intent: android.content.Intent, onPicked: (Uri) -> Unit) {
        onImagePickedCallback = onPicked
        imagePickerLauncher.launch(intent)
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* aceita ou nega — seguimos normalmente */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        refreshFcmToken()

        setContent {
            EasyChatTheme {
                // MainScreen contém toda a navegação Bottom Nav (Chat + Profile)
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModel.updateFcmToken(token)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "easychat_channel", "Mensagens", NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notificações de novas mensagens" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}