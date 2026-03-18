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

    init {
        loadUser()
    }

    fun loadUser() {
        _loading.value = true
        viewModelScope.launch {
            val userModel = userRepository.getCurrentUser()
            _user.postValue(userModel)
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
                var avatarUrl = currentUser.avatarUrl

                // Upload de nova foto se selecionada
                if (imageUri != null) {
                    val bytes = contentResolver.openInputStream(imageUri)?.readBytes()
                    if (bytes != null) {
                        avatarUrl = mediaRepository.uploadAvatar(
                            userId = SupabaseClientProvider.currentUserId(),
                            bytes = bytes
                        )
                    }
                }

                val updatedUser = currentUser.copy(
                    username = newUsername,
                    avatarUrl = avatarUrl
                )
                userRepository.saveUser(updatedUser)
                _user.postValue(updatedUser)
                _updateResult.postValue(true)
            } catch (e: Exception) {
                _updateResult.postValue(false)
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun clearUpdateResult() {
        _updateResult.value = null
    }
}
