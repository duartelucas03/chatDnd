package com.example.easychat.ui.auth

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.databinding.ActivitySplashBinding
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val extras = intent.extras
        if (extras != null) {
            // Aberto via notificação push — vai direto para o chat
            val userId = extras.getString("userId") ?: return
            CoroutineScope(Dispatchers.IO).launch {
                val user = userRepository.getUserById(userId) ?: return@launch
                withContext(Dispatchers.Main) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                    })
                    startActivity(Intent(this@SplashActivity, ChatActivity::class.java).apply {
                        AndroidUtil.passUserModelAsIntent(this, user)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
                    finish()
                }
            }
        } else {
            Handler(Looper.getMainLooper()).postDelayed({
                val destination = if (SupabaseClientProvider.isLoggedIn()) {
                    Intent(this, MainActivity::class.java)
                } else {
                    Intent(this, LoginPhoneNumberActivity::class.java)
                }
                startActivity(destination)
                finish()
            }, 1000)
        }
    }
}
