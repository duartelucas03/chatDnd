package com.example.easychat.ui.auth

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.easychat.R
import com.example.easychat.ui.compose.components.EasyChatTextField
import com.example.easychat.ui.compose.components.PrimaryButton
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil

@Composable
fun LoginUsernameScreen(
    phoneNumber: String,
    viewModel: LoginUsernameViewModel = viewModel()
) {
    val context = LocalContext.current
    val loading         by viewModel.loading.collectAsStateWithLifecycle()
    val existingUsername by viewModel.existingUsername.collectAsStateWithLifecycle()
    val saveResult      by viewModel.saveResult.collectAsStateWithLifecycle()

    var username     by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }

    // Carrega usuário existente ao montar
    LaunchedEffect(Unit) { viewModel.loadExistingUser() }

    // Preenche o campo se já existe username
    LaunchedEffect(existingUsername) {
        existingUsername?.let { if (username.isEmpty()) username = it }
    }

    // Reage ao resultado do save
    LaunchedEffect(saveResult) {
        when (saveResult) {
            true -> {
                context.startActivity(Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            }
            false -> {
                AndroidUtil.showToast(context, "Erro ao salvar username. Tente novamente.")
                viewModel.clearSaveResult()
            }
            null -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.person_icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(80.dp)
        )
        Spacer(Modifier.height(24.dp))
        Text("Escolha seu username", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        EasyChatTextField(
            value = username,
            onValueChange = { username = it; usernameError = null },
            label = "Username",
            isError = usernameError != null,
            errorMessage = usernameError,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            onImeAction = {
                if (username.trim().length < 3) {
                    usernameError = "Username deve ter ao menos 3 caracteres"
                } else {
                    viewModel.saveUsername(phoneNumber, username.trim())
                }
            }
        )
        Spacer(Modifier.height(24.dp))

        PrimaryButton(
            text = "Entrar",
            isLoading = loading,
            onClick = {
                val trimmed = username.trim()
                if (trimmed.length < 3) {
                    usernameError = "Username deve ter ao menos 3 caracteres"
                    return@PrimaryButton
                }
                viewModel.saveUsername(phoneNumber, trimmed)
            }
        )
    }
}