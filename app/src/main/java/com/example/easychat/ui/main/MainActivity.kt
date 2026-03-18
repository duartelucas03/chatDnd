package com.example.easychat.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.easychat.R
import com.example.easychat.databinding.ActivityMainBinding
import com.example.easychat.ui.profile.ProfileFragment
import com.example.easychat.ui.search.SearchUserActivity
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    val viewModel: MainViewModel by viewModels()

    private val chatFragment    = ChatFragment()
    private val profileFragment = ProfileFragment()

    // Launcher para pedir permissão de notificação (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Usuário respondeu — concedeu ou negou. Em ambos os casos, seguimos normalmente.
        // Se negou, simplesmente não receberá notificações em foreground.
        // Não precisamos fazer nada aqui além de registrar o token FCM,
        // pois o canal já foi criado antes da solicitação.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()
        requestNotificationPermissionIfNeeded()

        binding.mainSearchBtn.setOnClickListener {
            startActivity(Intent(this, SearchUserActivity::class.java))
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.menu_chat -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_frame_layout, chatFragment).commit()
                    true
                }
                R.id.menu_profile -> {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_frame_layout, profileFragment).commit()
                    true
                }
                else -> false
            }
        }

        binding.bottomNavigation.selectedItemId = R.id.menu_chat
        refreshFcmToken()
    }

    override fun onResume() {
        super.onResume()
        viewModel.setUserOnline()
    }

    override fun onPause() {
        super.onPause()
        viewModel.setUserOffline()
    }

    /**
     * No Android 13 (API 33) e acima, POST_NOTIFICATIONS é uma permissão
     * "perigosa" que precisa ser solicitada em runtime, igual câmera e localização.
     * Em versões anteriores a permissão é concedida automaticamente na instalação.
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                // Já tem permissão — não faz nada
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> { /* ok */ }

                // Android recomenda mostrar explicação antes de pedir novamente
                // se o usuário já negou uma vez — aqui usamos o diálogo padrão do sistema,
                // que é suficiente para a maioria dos apps de chat.
                else -> notificationPermissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
    }

    private fun refreshFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            viewModel.updateFcmToken(token)
        }
    }

    private fun createNotificationChannel() {
        val channel = android.app.NotificationChannel(
            "easychat_channel",
            "Mensagens",
            android.app.NotificationManager.IMPORTANCE_HIGH
        ).apply { description = "Notificações de novas mensagens" }
        getSystemService(android.app.NotificationManager::class.java)
            .createNotificationChannel(channel)
    }
}
