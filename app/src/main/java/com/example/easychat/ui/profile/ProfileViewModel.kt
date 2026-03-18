package com.example.easychat.ui.profile

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.MediaRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val mediaRepository: MediaRepository = MediaRepository()
) : ViewModel() {

    private val _user = MutableLiveData<UserModel?>()
    val user: LiveData<UserModel?> = _user

    private val _updateResult = MutableLiveData<Boolean?>()
    val updateResult: LiveData<Boolean?> = _updateResult

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private var pendingAvatarUrl: String? = null

    init { loadUser() }

    fun loadUser() {
        _loading.value = true
        viewModelScope.launch {
            _user.postValue(userRepository.getCurrentUser())
            _loading.postValue(false)
        }
    }

    fun updateProfile(
        newUsername: String,
        newStatusMessage: String,
        imageUri: Uri?,
        contentResolver: android.content.ContentResolver
    ) {
        val currentUser = _user.value ?: return
        _loading.value = true
        viewModelScope.launch {
            try {
                var avatarUrl = pendingAvatarUrl ?: currentUser.avatarUrl
                if (imageUri != null) {
                    val bytes = contentResolver.openInputStream(imageUri)?.readBytes()
                    if (bytes != null) {
                        avatarUrl = mediaRepository.uploadAvatar(
                            userId = SupabaseClientProvider.currentUserId(),
                            bytes  = bytes
                        )
                        pendingAvatarUrl = avatarUrl
                    }
                }
                val updated = currentUser.copy(
                    username      = newUsername,
                    statusMessage = newStatusMessage,
                    avatarUrl     = avatarUrl
                )
                userRepository.saveUser(updated)
                _user.postValue(updated)
                _updateResult.postValue(true)
            } catch (e: Exception) {
                _updateResult.postValue(false)
            } finally {
                _loading.postValue(false)
            }
        }
    }

    /**
     * Logout usa GlobalScope + NonCancellable intencionalmente:
     * - GlobalScope: não é cancelado quando o ViewModel é destruído (o que pode
     *   acontecer durante a navegação pós-logout, matando o viewModelScope)
     * - NonCancellable: garante que o finally/onLogoutDone sempre execute,
     *   mesmo que a coroutine sofra cancelamento externo
     * - onLogoutDone é chamado na main thread sempre, com ou sem erro
     */
    @Suppress("OPT_IN_USAGE")
    fun logout(onLogoutDone: () -> Unit) {
        android.util.Log.d("LOGOUT", "1 - logout() chamado")
        GlobalScope.launch(Dispatchers.IO) {
            android.util.Log.d("LOGOUT", "2 - coroutine iniciada")
            withContext(NonCancellable) {
                try {
                    android.util.Log.d("LOGOUT", "3 - limpando FCM token")
                    try {
                        userRepository.updateFcmToken("")
                        android.util.Log.d("LOGOUT", "4 - FCM token limpo")
                    } catch (e: Exception) {
                        android.util.Log.w("LOGOUT", "4w - FCM falhou (ignorado): ${e.message}")
                    }
                    android.util.Log.d("LOGOUT", "5 - chamando signOut()")
                    SupabaseClientProvider.auth.signOut()
                    android.util.Log.d("LOGOUT", "6 - signOut() concluído")
                } catch (e: Exception) {
                    android.util.Log.e("LOGOUT", "ERRO: ${e::class.simpleName} - ${e.message}")
                } finally {
                    android.util.Log.d("LOGOUT", "7 - chamando onLogoutDone na main thread")
                    withContext(Dispatchers.Main) {
                        android.util.Log.d("LOGOUT", "8 - onLogoutDone() executando")
                        onLogoutDone()
                    }
                }
            }
        }
    }

    fun clearUpdateResult() { _updateResult.value = null }
}
