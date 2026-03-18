package com.example.easychat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.easychat.databinding.ActivitySplashBinding
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val viewModel: SplashViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val notificationUserId = intent.extras?.getString("userId")

        // Delay mínimo para a splash ser visível apenas na abertura normal
        val delay = if (notificationUserId != null) 0L else 1000L

        Handler(Looper.getMainLooper()).postDelayed({
            viewModel.resolveDestination(notificationUserId)
        }, delay)

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.destination.observe(this) { destination ->
            destination ?: return@observe
            when (destination) {
                is SplashDestination.Main -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
                is SplashDestination.Login -> {
                    startActivity(Intent(this, LoginPhoneNumberActivity::class.java))
                    finish()
                }
                is SplashDestination.ChatFromNotification -> {
                    // Abre MainActivity em background + ChatActivity no topo
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    })
                    startActivity(Intent(this, ChatActivity::class.java).apply {
                        AndroidUtil.passUserModelAsIntent(this, destination.user)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            }
            viewModel.clearDestination()
        }
    }
}
