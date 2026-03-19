
package com.example.easychat.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.utils.SupabaseClientProvider
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Claude AI - início
 * Prompt: Crie o ViewModel da tela de login por telefone. Ele precisa enviar o OTP pelo Supabase e depois verificar o código que o usuário digitou, expondo os estados de loading, sucesso e erro via StateFlow.
 */

sealed class AuthState {
    object Idle          : AuthState()
    object Loading       : AuthState()
    object CodeSent      : AuthState()
    object SignInSuccess : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = SupabaseClientProvider.auth

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var phoneNumber: String = ""

    fun sendOtp(phone: String) {
        phoneNumber = phone
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.signInWith(OTP) { this.phone = phone }
                _authState.value = AuthState.CodeSent
            } catch (e: Exception) {
                _authState.value = AuthState.Error("Falha ao enviar OTP: ${e.message}")
            }
        }
    }

    fun verifyOtp(otp: String) {
        if (phoneNumber.isEmpty()) {
            _authState.value = AuthState.Error("Número de telefone não informado")
            return
        }
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.verifyPhoneOtp(type = OtpType.Phone.SMS, phone = phoneNumber, token = otp)
                _authState.value = AuthState.SignInSuccess
            } catch (e: Exception) {
                _authState.value = AuthState.Error("OTP inválido: ${e.message}")
            }
        }
    }

    fun resetState() { _authState.value = AuthState.Idle }
}

/** Claude AI - final */