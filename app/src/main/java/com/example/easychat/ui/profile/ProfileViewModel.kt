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
import kotlinx.coroutines.launch

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

    // Evento único: sinaliza que o logout foi concluído e a View deve navegar
    private val _logoutComplete = MutableLiveData<Boolean>(false)
    val logoutComplete: LiveData<Boolean> = _logoutComplete

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
                val updated = currentUser.copy(username = newUsername, avatarUrl = avatarUrl)
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
     * Lógica de logout pertence ao ViewModel, não à View.
     * O FCM token é limpo antes de encerrar a sessão no Supabase.
     */
    fun logout(onFcmTokenDeleted: (() -> Unit) -> Unit) {
        onFcmTokenDeleted {
            viewModelScope.launch {
                try {
                    userRepository.updateFcmToken("")   // limpa token no banco
                    SupabaseClientProvider.auth.signOut()
                } catch (e: Exception) { /* ignora */ } finally {
                    _logoutComplete.postValue(true)
                }
            }
        }
    }

    fun clearUpdateResult() { _updateResult.value = null }
    fun clearLogoutComplete() { _logoutComplete.value = false }
}
