
package com.example.easychat.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Claude AI - início
 * Prompt: Crie o ViewModel da splash screen. Ele decide pra onde o app vai: se o usuário não está logado vai pro login, se está vai pra tela principal, e se veio de uma notificação vai direto pro chat daquele usuário.
 */

sealed class SplashDestination {
    object Main                                          : SplashDestination()
    object Login                                         : SplashDestination()
    data class ChatFromNotification(val user: UserModel) : SplashDestination()
}

class SplashViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _destination = MutableStateFlow<SplashDestination?>(null)
    val destination: StateFlow<SplashDestination?> = _destination.asStateFlow()

    fun resolveDestination(notificationUserId: String?) {
        if (!notificationUserId.isNullOrBlank()) {
            viewModelScope.launch {
                val user = userRepository.getUserById(notificationUserId)
                _destination.value = if (user != null)
                    SplashDestination.ChatFromNotification(user)
                else
                    SplashDestination.Main
            }
        } else {
            _destination.value = if (SupabaseClientProvider.isLoggedIn())
                SplashDestination.Main
            else
                SplashDestination.Login
        }
    }

    fun clearDestination() { _destination.value = null }
}

/** Claude AI - final */