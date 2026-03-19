package com.example.easychat.ui.search

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.easychat.R
import com.example.easychat.model.UserModel
import com.example.easychat.ui.chat.ChatActivity
import com.example.easychat.ui.compose.components.SearchUserRow
import com.example.easychat.utils.AndroidUtil
import com.example.easychat.utils.SupabaseClientProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val context      = LocalContext.current
    val results      by viewModel.results.collectAsStateWithLifecycle()
    val loading      by viewModel.loading.collectAsStateWithLifecycle()
    val groupCreated by viewModel.groupCreated.collectAsStateWithLifecycle()
    val error        by viewModel.error.collectAsStateWithLifecycle()

    var searchTerm     by remember { mutableStateOf("") }
    var searchError    by remember { mutableStateOf<String?>(null) }
    var selectionMode  by remember { mutableStateOf(false) }
    // Map id → UserModel, igual ao original — preserva seleção entre buscas
    val selectedUsers  = remember { mutableStateMapOf<String, UserModel>() }
    var showGroupDialog by remember { mutableStateOf(false) }

    val contactsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) importContacts(context, viewModel)
        else android.widget.Toast.makeText(context, "Permissão de contatos negada", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Reage ao grupo criado
    LaunchedEffect(groupCreated) {
        val room = groupCreated ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, "Grupo \"${room.name}\" criado!", android.widget.Toast.LENGTH_SHORT).show()
        selectionMode = false
        selectedUsers.clear()
        viewModel.clearGroupCreated()
        val intent = Intent(context, ChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        AndroidUtil.passChatroomIdAsIntent(intent, room.id, room.name ?: "Grupo", room.avatarUrl)
        context.startActivity(intent)
        onBack()
    }

    // Exibe erro
    LaunchedEffect(error) {
        error ?: return@LaunchedEffect
        android.widget.Toast.makeText(context, error, android.widget.Toast.LENGTH_LONG).show()
        viewModel.clearError()
    }

    // Dialog de nome do grupo
    if (showGroupDialog) {
        GroupNameDialog(
            memberCount = selectedUsers.size,
            onConfirm = { name ->
                showGroupDialog = false
                viewModel.createGroup(name, selectedUsers.values.toList())
            },
            onDismiss = { showGroupDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buscar usuários") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = "Voltar")
                    }
                },
                actions = {
                    // Long press no ícone de busca importa contatos
                    IconButton(
                        onClick = { /* curto: nada */ },
                        modifier = Modifier
                    ) {
                        Icon(painterResource(R.drawable.icon_search), contentDescription = "Buscar")
                    }
                    IconButton(onClick = {
                        if (selectionMode) {
                            selectionMode = false
                            selectedUsers.clear()
                        } else {
                            if (results.isEmpty()) {
                                android.widget.Toast.makeText(context, "Busque usuários antes de criar um grupo", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                selectionMode = true
                            }
                        }
                    }) {
                        Icon(painterResource(R.drawable.ic_group), contentDescription = "Criar grupo")
                    }
                }
            )
        },
        bottomBar = {
            if (selectionMode) {
                Surface(shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${selectedUsers.size} selecionado(s)", fontWeight = FontWeight.Medium)
                        Button(
                            onClick = { if (selectedUsers.isNotEmpty()) showGroupDialog = true },
                            enabled = selectedUsers.isNotEmpty()
                        ) { Text("Criar grupo") }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // ── Barra de busca ───────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchTerm,
                    onValueChange = { searchTerm = it; searchError = null },
                    placeholder = { Text("Buscar por username") },
                    isError = searchError != null,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                    trailingIcon = {
                        // Long press → importar contatos
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
                                == PackageManager.PERMISSION_GRANTED
                            ) importContacts(context, viewModel)
                            else contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                        }) {
                            // Reusa o ícone de busca; long press não existe no Compose puro,
                            // então o import de contatos fica num botão separado dedicado
                        }
                    }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (searchTerm.trim().length < 3) {
                            searchError = "Digite ao menos 3 caracteres"
                        } else {
                            viewModel.searchUsers(searchTerm.trim())
                        }
                    },
                    enabled = !loading
                ) {
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(painterResource(R.drawable.icon_search), contentDescription = "Buscar")
                }
            }
            if (searchError != null) {
                Text(
                    text = searchError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // ── Lista de resultados ──────────────────────────────────────────
            val currentUserId = SupabaseClientProvider.currentUserId()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(items = results, key = { it.id }) { user ->
                    val isSelf = user.id == currentUserId
                    SearchUserRow(
                        username      = user.username,
                        phone         = user.phone,
                        avatarUrl     = user.avatarUrl,
                        isSelected    = selectedUsers.containsKey(user.id),
                        selectionMode = selectionMode,
                        isSelf        = isSelf,
                        onClick = {
                            if (selectionMode && !isSelf) {
                                // Toggle seleção
                                if (selectedUsers.containsKey(user.id))
                                    selectedUsers.remove(user.id)
                                else
                                    selectedUsers[user.id] = user
                            } else if (!isSelf) {
                                // Abre chat direto
                                val intent = Intent(context, ChatActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                AndroidUtil.passUserModelAsIntent(intent, user)
                                context.startActivity(intent)
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                }
            }
        }
    }
}

// ─── Dialog de nome do grupo ────────────────────────────────────────────────

@Composable
private fun GroupNameDialog(
    memberCount: Int,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo Grupo") },
        text = {
            Column {
                Text("$memberCount participante(s) selecionado(s)")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Nome do grupo") },
                    isError = error != null,
                    singleLine = true,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.trim().length < 2) { error = "Nome deve ter ao menos 2 caracteres"; return@TextButton }
                onConfirm(name.trim())
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// ─── Importação de contatos (lógica idêntica ao original) ───────────────────

private fun importContacts(context: android.content.Context, viewModel: SearchViewModel) {
    val phones = mutableListOf<String>()
    val cursor = context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        null, null, null
    )
    cursor?.use {
        val colIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
        while (it.moveToNext()) {
            val raw = it.getString(colIdx) ?: continue
            val normalized = raw.replace(Regex("[^+\\d]"), "")
            if (normalized.length >= 8) phones.add(normalized)
        }
    }
    if (phones.isEmpty()) {
        android.widget.Toast.makeText(context, "Nenhum contato encontrado", android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    android.widget.Toast.makeText(context, "Buscando ${phones.size} contatos...", android.widget.Toast.LENGTH_SHORT).show()
    viewModel.searchByPhones(phones.distinct())
}