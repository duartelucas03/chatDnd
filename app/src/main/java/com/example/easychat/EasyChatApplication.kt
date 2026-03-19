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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class EasyChatApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val userRepository by lazy { UserRepository() }

    override fun onCreate() {
        super.onCreate()

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch {
                    // Tenta várias vezes — garante que funciona tanto na
                    // primeira abertura (sessão carregando) quanto nas seguintes
                    repeat(5) { attempt ->
                        if (SupabaseClientProvider.isLoggedIn()) {
                            try { userRepository.updateStatus("online") } catch (e: Exception) { }
                            return@launch
                        }
                        delay(800L * (attempt + 1)) // 800ms, 1600ms, 2400ms...
                    }
                }
            }

            override fun onStop(owner: LifecycleOwner) {
                if (!SupabaseClientProvider.isLoggedIn()) return
                runBlocking {
                    try {
                        withTimeout(3000) { userRepository.updateStatus("offline") }
                    } catch (e: Exception) { }
                }
            }
        })
    }
}