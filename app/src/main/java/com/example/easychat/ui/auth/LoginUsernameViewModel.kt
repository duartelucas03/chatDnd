
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
 * Prompt: Crie o ViewModel da tela onde o usuário escolhe seu nome de usuário após o login. Ele carrega o nome atual se já existir, e salva o novo nome no Supabase quando o usuário confirmar.
 */
class LoginUsernameViewModel(
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _saveResult = MutableStateFlow<Boolean?>(null)
    val saveResult: StateFlow<Boolean?> = _saveResult.asStateFlow()

    private val _existingUsername = MutableStateFlow<String?>(null)
    val existingUsername: StateFlow<String?> = _existingUsername.asStateFlow()

    fun loadExistingUser() {
        _loading.value = true
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _existingUsername.value = user?.username
            _loading.value = false
        }
    }

    fun saveUsername(phone: String, username: String) {
        _loading.value = true
        viewModelScope.launch {
            try {
                val currentId = SupabaseClientProvider.currentUserId()
                val existing  = userRepository.getCurrentUser()
                val user = existing?.copy(username = username)
                    ?: UserModel(id = currentId, phone = phone, username = username)
                userRepository.saveUser(user)
                _saveResult.value = true
            } catch (e: Exception) {
                _saveResult.value = false
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearSaveResult() { _saveResult.value = null }
}

/** Claude AI - final */