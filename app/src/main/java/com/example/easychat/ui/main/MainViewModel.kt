package com.example.easychat.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.ChatRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.ChatroomModel
import com.example.easychat.model.RecentChatUiModel
import com.example.easychat.utils.CryptoManager
import com.example.easychat.utils.SupabaseClientProvider
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainViewModel(
    private val chatRepository: ChatRepository = ChatRepository(),
    private val userRepository: UserRepository = UserRepository()
) : ViewModel() {

    private val _recentChats = MutableLiveData<List<RecentChatUiModel>>(emptyList())
    val recentChats: LiveData<List<RecentChatUiModel>> = _recentChats

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    init {
        loadRecentChats()
        subscribeToRecentChatsRealtime()
    }

    fun loadRecentChats() {
        _loading.value = true
        viewModelScope.launch {
            try {
                val rooms = chatRepository.getRecentChats(SupabaseClientProvider.currentUserId())
                _recentChats.postValue(buildUiModels(rooms))
            } catch (e: Exception) {
                _recentChats.postValue(emptyList())
            } finally {
                _loading.postValue(false)
            }
        }
    }

    /**
     * Monta a lista de UiModels:
     * - resolve o outro usuário de cada chat direto (query em batch)
     * - descriptografa a última mensagem
     * - formata horário
     * O Adapter recebe dados 100% prontos — sem lógica de negócio na View.
     */
    private suspend fun buildUiModels(rooms: List<ChatroomModel>): List<RecentChatUiModel> =
        withContext(Dispatchers.IO) {
            val currentId = SupabaseClientProvider.currentUserId()

            // Coleta todos os IDs de membros de chats diretos de uma vez (evita N queries)
            val directRoomIds = rooms.filter { !it.isGroup }.map { it.id }
            val membersByRoom = if (directRoomIds.isEmpty()) emptyMap()
            else {
                directRoomIds.associateWith { roomId ->
                    try {
                        chatRepository.getMembersOfChatroom(roomId).map { it.userId }
                    } catch (e: Exception) { emptyList() }
                }
            }

            // Coleta todos os otherUserIds únicos e busca em batch
            val otherUserIds = membersByRoom.values
                .flatten()
                .filter { it != currentId }
                .distinct()
            val usersById = if (otherUserIds.isEmpty()) emptyMap()
            else userRepository.getUsersByIds(otherUserIds).associateBy { it.id }

            rooms.map { room ->
                val otherUser = if (!room.isGroup) {
                    val membersIds = membersByRoom[room.id].orEmpty()
                    val otherId = membersIds.firstOrNull { it != currentId }
                    otherId?.let { usersById[it] }
                } else null

                val displayName = when {
                    room.isGroup -> room.name ?: "Grupo"
                    otherUser != null -> otherUser.username
                    else -> "Usuário"
                }

                RecentChatUiModel(
                    chatroomId          = room.id,
                    displayName         = displayName,
                    avatarUrl           = otherUser?.avatarUrl ?: room.avatarUrl,
                    lastMessagePreview  = buildPreview(room, currentId),
                    lastMessageTime     = formatTime(room.lastMessageAt),
                    isGroup             = room.isGroup,
                    otherUser           = otherUser
                )
            }
        }

    private fun buildPreview(room: ChatroomModel, currentId: String): String {
        val isMe = room.lastMessageSenderId == currentId
        val prefix = if (isMe) "Você: " else ""
        return when (room.lastMessageType) {
            "image"    -> "${prefix}📷 Foto"
            "audio"    -> "${prefix}🎵 Áudio"
            "location" -> "${prefix}📍 Localização"
            else -> {
                val decrypted = CryptoManager.decrypt(room.lastMessage ?: "")
                "$prefix$decrypted"
            }
        }
    }

    private fun formatTime(lastMessageAt: String?): String = try {
        val instant = Instant.parse(lastMessageAt)
        val local   = instant.atZone(ZoneId.systemDefault())
        val today   = LocalDate.now(ZoneId.systemDefault())
        if (local.toLocalDate() == today)
            DateTimeFormatter.ofPattern("HH:mm").format(local)
        else
            DateTimeFormatter.ofPattern("dd/MM").format(local)
    } catch (e: Exception) { "" }

    private fun subscribeToRecentChatsRealtime() {
        viewModelScope.launch {
            try {
                val channel = SupabaseClientProvider.realtime
                    .channel("chatrooms_preview_${System.currentTimeMillis()}")
                realtimeChannel = channel

                channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "chatrooms"
                }.onEach { _ ->
                    loadRecentChats()
                }.launchIn(viewModelScope)

                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { _ ->
                    loadRecentChats()
                }.launchIn(viewModelScope)

                channel.subscribe()
            } catch (e: Exception) {
                android.util.Log.e("MAIN_REALTIME", "Erro: ${e.message}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try { realtimeChannel?.let { SupabaseClientProvider.realtime.removeChannel(it) } }
            catch (e: Exception) { /* ignora */ }
        }
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            try { userRepository.updateFcmToken(token) } catch (e: Exception) { /* silencioso */ }
        }
    }

    fun setUserOnline() {
        viewModelScope.launch {
            try { userRepository.updateStatus("online") } catch (e: Exception) { /* silencioso */ }
        }
    }

    fun setUserOffline() {
        viewModelScope.launch {
            try { userRepository.updateStatus("offline") } catch (e: Exception) { /* silencioso */ }
        }
    }
}
