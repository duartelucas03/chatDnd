package com.example.easychat.ui.auth

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.easychat.R
import com.example.easychat.ui.compose.components.PrimaryButton
import com.example.easychat.utils.AndroidUtil

@Composable
fun LoginOtpScreen(
    phoneNumber: String,
    viewModel: AuthViewModel = viewModel()
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var otpValue by remember { mutableStateOf("") }
    var otpError by remember { mutableStateOf<String?>(null) }

    // Envia OTP ao montar a tela (mesmo comportamento do onCreate original)
    LaunchedEffect(Unit) {
        viewModel.sendOtp(phoneNumber)
    }

    // Reage às mudanças de estado
    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthState.CodeSent     -> AndroidUtil.showToast(context, "OTP enviado com sucesso")
            is AuthState.SignInSuccess -> {
                context.startActivity(Intent(context, LoginUsernameActivity::class.java).apply {
                    putExtra("phone", phoneNumber)
                })
            }
            is AuthState.Error -> {
                AndroidUtil.showToast(context, state.message)
                viewModel.resetState()
            }
            else -> Unit
        }
    }

    val isLoading = authState is AuthState.Loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.otp_icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("Verificação OTP", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Código enviado para $phoneNumber",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = otpValue,
            onValueChange = { if (it.length <= 6) otpValue = it; otpError = null },
            label = { Text("Código OTP") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = otpError != null,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        )
        if (otpError != null) {
            Text(
                text = otpError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Verificar",
            isLoading = isLoading,
            onClick = {
                if (otpValue.length < 6) {
                    otpError = "OTP inválido"
                    return@PrimaryButton
                }
                viewModel.verifyOtp(otpValue)
            }
        )
        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = { viewModel.sendOtp(phoneNumber) },
            enabled = !isLoading
        ) {
            Text("Reenviar OTP")
        }
    }
}