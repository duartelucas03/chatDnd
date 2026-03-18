package com.example.easychat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

class LoginUsernameViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _loading = MutableLiveData<Boolean>()
    val loading: LiveData<Boolean> = _loading

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> = _saveResult

    private val _existingUsername = MutableLiveData<String?>()
    val existingUsername: LiveData<String?> = _existingUsername

    fun loadExistingUser() {
        _loading.value = true
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _existingUsername.postValue(user?.username)
            _loading.postValue(false)
        }
    }

    fun saveUsername(phone: String, username: String) {
        _loading.value = true
        viewModelScope.launch {
            try {
                val currentId = SupabaseClientProvider.currentUserId()
                // Tenta carregar usuário existente para preservar dados anteriores
                val existing = userRepository.getCurrentUser()
                val user = existing?.copy(username = username)
                    ?: UserModel(
                        id = currentId,
                        phone = phone,
                        username = username
                    )
                userRepository.saveUser(user)
                _saveResult.postValue(true)
            } catch (e: Exception) {
                _saveResult.postValue(false)
            } finally {
                _loading.postValue(false)
            }
        }
    }
}
