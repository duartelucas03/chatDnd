package com.example.easychat.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.easychat.R
import com.example.easychat.databinding.ActivityMainBinding
import com.example.easychat.ui.profile.ProfileFragment
import com.example.easychat.ui.search.SearchUserActivity
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // FIX: ViewModel único na Activity — compartilhado com os fragments via activityViewModels()
    val viewModel: MainViewModel by viewModels()

    private val chatFragment    = ChatFragment()
    private val profileFragment = ProfileFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createNotificationChannel()

        // FIX: listener registrado apenas uma vez (era duplicado)
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
