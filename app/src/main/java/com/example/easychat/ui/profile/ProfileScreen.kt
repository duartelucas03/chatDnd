package com.example.easychat.ui.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.easychat.R
import com.example.easychat.ui.auth.SplashActivity
import com.example.easychat.ui.compose.components.EasyChatTextField
import com.example.easychat.ui.compose.components.PrimaryButton
import com.example.easychat.ui.main.MainActivity
import com.example.easychat.utils.AndroidUtil
import com.github.dhaval2404.imagepicker.ImagePicker
import com.google.firebase.messaging.FirebaseMessaging

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel()) {
    val context        = LocalContext.current
    val activity = LocalContext.current as? androidx.activity.ComponentActivity
    val user           by viewModel.user.collectAsStateWithLifecycle()
    val loading        by viewModel.loading.collectAsStateWithLifecycle()
    val updateResult   by viewModel.updateResult.collectAsStateWithLifecycle()

    var username        by remember { mutableStateOf("") }
    var statusMessage   by remember { mutableStateOf("") }
    var usernameError   by remember { mutableStateOf<String?>(null) }
    var selectedUri     by remember { mutableStateOf<Uri?>(null) }

    // Preenche os campos quando o user carrega
    LaunchedEffect(user) {
        user?.let {
            if (username.isEmpty()) username = it.username
            if (statusMessage.isEmpty()) statusMessage = it.statusMessage
        }
    }

    // Toast de resultado do update
    LaunchedEffect(updateResult) {
        updateResult ?: return@LaunchedEffect
        AndroidUtil.showToast(context, if (updateResult == true) "Perfil atualizado!" else "Falha ao atualizar")
        viewModel.clearUpdateResult()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Avatar clicável ─────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .clickable {
                    val permission = if (android.os.Build.VERSION.SDK_INT >= 33)
                        Manifest.permission.READ_MEDIA_IMAGES
                    else
                        Manifest.permission.READ_EXTERNAL_STORAGE

                    if (ContextCompat.checkSelfPermission(context, permission)
                        != PackageManager.PERMISSION_GRANTED
                    ) {
                        activity?.requestPermissions(arrayOf(permission), 100)
                        return@clickable
                    }

                    val mainActivity = activity as? MainActivity ?: return@clickable
                    ImagePicker.with(activity)
                        .cropSquare().compress(512).maxResultSize(512, 512)
                        .createIntent { intent ->
                            mainActivity.launchImagePicker(intent) { uri ->
                                selectedUri = uri
                            }
                        }
                },
            contentAlignment = Alignment.Center
        ) {
            val imageModel = selectedUri ?: user?.avatarUrl
            if (imageModel != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(imageModel).crossfade(true).build(),
                    contentDescription = "Foto de perfil",
                    contentScale = coil.size.Scale.FILL.let { androidx.compose.ui.layout.ContentScale.Crop },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.person_icon),
                    contentDescription = "Avatar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("Toque para alterar foto", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        // ── Campos ──────────────────────────────────────────────────────────
        EasyChatTextField(
            value = username,
            onValueChange = { username = it; usernameError = null },
            label = "Username",
            isError = usernameError != null,
            errorMessage = usernameError
        )
        Spacer(Modifier.height(12.dp))
        EasyChatTextField(
            value = user?.phone ?: "",
            onValueChange = {},
            label = "Telefone",
            enabled = false
        )
        Spacer(Modifier.height(12.dp))
        EasyChatTextField(
            value = statusMessage,
            onValueChange = { statusMessage = it },
            label = "Status",
            singleLine = false,
            maxLines = 3
        )
        Spacer(Modifier.height(24.dp))

        // ── Botão atualizar ──────────────────────────────────────────────────
        PrimaryButton(
            text = "Atualizar perfil",
            isLoading = loading,
            onClick = {
                val trimmed = username.trim()
                if (trimmed.length < 3) {
                    usernameError = "Username deve ter ao menos 3 caracteres"
                    return@PrimaryButton
                }
                val currentUser = user
                if (currentUser == null) {
                    AndroidUtil.showToast(context, "Aguarde carregar o perfil")
                    return@PrimaryButton
                }
                val newStatus = statusMessage.trim()
                if (selectedUri == null && trimmed == currentUser.username && newStatus == currentUser.statusMessage) {
                    AndroidUtil.showToast(context, "Nenhuma alteração detectada")
                    return@PrimaryButton
                }
                viewModel.updateProfile(trimmed, newStatus, selectedUri, context.contentResolver)
                selectedUri = null
            }
        )
        Spacer(Modifier.height(12.dp))

        // ── Logout ───────────────────────────────────────────────────────────
        OutlinedButton(
            onClick = {
                FirebaseMessaging.getInstance().deleteToken()
                viewModel.logout {
                    context.startActivity(Intent(context, SplashActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
        ) {
            Text("Sair", fontWeight = FontWeight.Medium, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

// Helper inline pois ContentScale.Crop não precisa de import extra
@Suppress("unused")
private val cropScale = androidx.compose.ui.layout.ContentScale.Crop