// ─────────────────────────────────────────────────────────────────────────────
// ui/profile/ProfileViewModel.kt
// ─────────────────────────────────────────────────────────────────────────────
package com.example.easychat.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.MediaRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val mediaRepository: MediaRepository = MediaRepository()
) : ViewModel() {

    private val _user = MutableStateFlow<UserModel?>(null)
    val user: StateFlow<UserModel?> = _user.asStateFlow()

    private val _updateResult = MutableStateFlow<Boolean?>(null)
    val updateResult: StateFlow<Boolean?> = _updateResult.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private var pendingAvatarUrl: String? = null

    init { loadUser() }

    fun loadUser() {
        _loading.value = true
        viewModelScope.launch {
            _user.value = userRepository.getCurrentUser()
            _loading.value = false
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
                _user.value = updated
                _updateResult.value = true
            } catch (e: Exception) {
                _updateResult.value = false
            } finally {
                _loading.value = false
            }
        }
    }

    @Suppress("OPT_IN_USAGE")
    fun logout(onLogoutDone: () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                try {
                    try { userRepository.updateFcmToken("") } catch (e: Exception) { }
                    SupabaseClientProvider.auth.signOut()
                } catch (e: Exception) {
                    android.util.Log.e("LOGOUT", "${e::class.simpleName}: ${e.message}")
                } finally {
                    withContext(Dispatchers.Main) { onLogoutDone() }
                }
            }
        }
    }

    fun clearUpdateResult() { _updateResult.value = null }
}