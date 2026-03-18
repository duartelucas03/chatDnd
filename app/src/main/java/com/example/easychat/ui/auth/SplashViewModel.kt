package com.example.easychat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

sealed class SplashDestination {
    object Main                         : SplashDestination()
    object Login                        : SplashDestination()
    data class ChatFromNotification(val user: UserModel) : SplashDestination()
}

class SplashViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _destination = MutableLiveData<SplashDestination?>()
    val destination: LiveData<SplashDestination?> = _destination

    fun resolveDestination(notificationUserId: String?) {
        if (!notificationUserId.isNullOrBlank()) {
            // Aberto via notificação — busca o usuário e navega para o chat
            viewModelScope.launch {
                val user = userRepository.getUserById(notificationUserId)
                if (user != null)
                    _destination.postValue(SplashDestination.ChatFromNotification(user))
                else
                    _destination.postValue(SplashDestination.Main)
            }
        } else {
            // Abertura normal — decide com base na sessão ativa
            _destination.value = if (SupabaseClientProvider.isLoggedIn())
                SplashDestination.Main
            else
                SplashDestination.Login
        }
    }

    fun clearDestination() { _destination.value = null }
}
