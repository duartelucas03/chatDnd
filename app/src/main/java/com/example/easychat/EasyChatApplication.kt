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
import kotlinx.coroutines.launch

class EasyChatApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Inicialização lazy — só cria quando o primeiro setStatus() for chamado,
    // garantindo que o Looper já está pronto e o Ktor pode inicializar corretamente
    private val userRepository by lazy { UserRepository() }

    override fun onCreate() {
        super.onCreate()

        // Registra o observer APÓS super.onCreate() — Looper já está configurado aqui
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                setStatus("online")
            }
            override fun onStop(owner: LifecycleOwner) {
                setStatus("offline")
            }
        })
    }

    private fun setStatus(status: String) {
        // Só opera se houver sessão ativa — evita chamadas desnecessárias ao banco
        if (!SupabaseClientProvider.isLoggedIn()) return
        appScope.launch {
            try {
                userRepository.updateStatus(status)
            } catch (e: Exception) {
                // Silencioso — falha de rede não deve impactar o app
            }
        }
    }
}
