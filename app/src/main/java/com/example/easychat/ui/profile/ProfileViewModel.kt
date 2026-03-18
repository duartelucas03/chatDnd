package com.example.easychat.ui.profile

import android.net.Uri
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.MediaRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * LiveData que dispara o observer apenas UMA VEZ por setValue/postValue.
 * Resolve o problema clássico de navegação/logout onde o observer é
 * re-entregue após rotação de tela ou recriação do Fragment.
 */
class SingleLiveEvent<T> : MutableLiveData<T>() {
    private val pending = AtomicBoolean(false)

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        super.observe(owner) { value ->
            if (pending.compareAndSet(true, false)) {
                observer.onChanged(value)
            }
        }
    }

    @MainThread
    override fun setValue(value: T?) {
        pending.set(true)
        super.setValue(value)
    }

    override fun postValue(value: T?) {
        pending.set(true)
        super.postValue(value)
    }
}

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

    // SingleLiveEvent: dispara apenas uma vez — nunca entrega "false" inicial ao observer
    private val _logoutEvent = SingleLiveEvent<Unit>()
    val logoutEvent: LiveData<Unit> = _logoutEvent

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

    fun logout(onFcmTokenDeleted: (() -> Unit) -> Unit) {
        onFcmTokenDeleted {
            viewModelScope.launch {
                try {
                    userRepository.updateFcmToken("")
                    SupabaseClientProvider.auth.signOut()
                } catch (e: Exception) {
                    /* ignora falhas de rede no logout */
                } finally {
                    // postValue garante entrega na main thread vinda de coroutine
                    _logoutEvent.postValue(Unit)
                }
            }
        }
    }

    fun clearUpdateResult() { _updateResult.value = null }
}

