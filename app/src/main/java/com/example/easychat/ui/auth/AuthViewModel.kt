package com.example.easychat.ui.auth

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.utils.SupabaseClientProvider
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.OtpType
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    object CodeSent : AuthState()
    object SignInSuccess : AuthState()
    data class Error(val message: String) : AuthState()
}

class AuthViewModel : ViewModel() {

    private val auth = SupabaseClientProvider.auth

    private val _authState = MutableLiveData<AuthState>(AuthState.Idle)
    val authState: LiveData<AuthState> = _authState

    private var phoneNumber: String = ""

    fun sendOtp(phone: String) {
        phoneNumber = phone
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                auth.signInWith(OTP) {
                    this.phone = phone
                }
                _authState.postValue(AuthState.CodeSent)
            } catch (e: Exception) {
                android.util.Log.e("SUPABASE_ERROR", "Erro sendOtp: ${e.message}", e)
                _authState.postValue(AuthState.Error("Falha ao enviar OTP: ${e.message}"))
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
                auth.verifyPhoneOtp(
                    type = OtpType.Phone.SMS,
                    phone = phoneNumber,
                    token = otp
                )
                _authState.postValue(AuthState.SignInSuccess)
            } catch (e: Exception) {
                _authState.postValue(AuthState.Error("OTP inválido: ${e.message}"))
            }
        }
    }


}
