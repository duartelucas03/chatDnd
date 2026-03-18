package com.example.easychat

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EasyChatApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userRepository by lazy { UserRepository() }

    override fun onCreate() {
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                setStatus("online", delayMs = 1500)
            }
            override fun onStop(owner: LifecycleOwner) {
                setStatus("offline", delayMs = 0)
            }
        })
    }

    private fun setStatus(status: String, delayMs: Long = 0) {
        appScope.launch {
            try {
                if (delayMs > 0) delay(delayMs)
                // Após o delay, o Auth já carregou a sessão do storage
                if (!SupabaseClientProvider.isLoggedIn()) return@launch
                userRepository.updateStatus(status)
            } catch (e: Exception) {
                // Silencioso — falha de rede não deve impactar o app
            }
        }
    }
}