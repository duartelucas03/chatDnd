// ─────────────────────────────────────────────────────────────────────────────
// ui/main/MainViewModel.kt
// ─────────────────────────────────────────────────────────────────────────────
package com.example.easychat.ui.main

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _recentChats = MutableStateFlow<List<RecentChatUiModel>>(emptyList())
    val recentChats: StateFlow<List<RecentChatUiModel>> = _recentChats.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

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
                _recentChats.value = buildUiModels(rooms)
            } catch (e: Exception) {
                _recentChats.value = emptyList()
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun buildUiModels(rooms: List<ChatroomModel>): List<RecentChatUiModel> =
        withContext(Dispatchers.IO) {
            val currentId = SupabaseClientProvider.currentUserId()
            val directRoomIds = rooms.filter { !it.isGroup }.map { it.id }
            val membersByRoom = if (directRoomIds.isEmpty()) emptyMap()
            else directRoomIds.associateWith { roomId ->
                try { chatRepository.getMembersOfChatroom(roomId).map { it.userId } }
                catch (e: Exception) { emptyList() }
            }
            val otherUserIds = membersByRoom.values.flatten().filter { it != currentId }.distinct()
            val usersById = if (otherUserIds.isEmpty()) emptyMap()
            else userRepository.getUsersByIds(otherUserIds).associateBy { it.id }

            rooms.map { room ->
                val otherUser = if (!room.isGroup) {
                    val otherId = membersByRoom[room.id].orEmpty().firstOrNull { it != currentId }
                    otherId?.let { usersById[it] }
                } else null

                val displayName = when {
                    room.isGroup    -> room.name ?: "Grupo"
                    otherUser != null -> otherUser.username
                    else            -> "Usuário"
                }
                RecentChatUiModel(
                    chatroomId         = room.id,
                    displayName        = displayName,
                    avatarUrl          = otherUser?.avatarUrl ?: room.avatarUrl,
                    lastMessagePreview = buildPreview(room, currentId),
                    lastMessageTime    = formatTime(room.lastMessageAt),
                    isGroup            = room.isGroup,
                    otherUser          = otherUser
                )
            }
        }

    private fun buildPreview(room: ChatroomModel, currentId: String): String {
        val isMe   = room.lastMessageSenderId == currentId
        val prefix = if (isMe) "Você: " else ""
        return when (room.lastMessageType) {
            "image"    -> "${prefix}📷 Foto"
            "audio"    -> "${prefix}🎵 Áudio"
            "location" -> "${prefix}📍 Localização"
            else       -> "$prefix${CryptoManager.decrypt(room.lastMessage ?: "")}"
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
                }.onEach { loadRecentChats() }.launchIn(viewModelScope)
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }.onEach { loadRecentChats() }.launchIn(viewModelScope)
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
            catch (e: Exception) { }
        }
    }

    fun updateFcmToken(token: String) {
        viewModelScope.launch {
            try { userRepository.updateFcmToken(token) } catch (e: Exception) { }
        }
    }

    fun setUserOnline()  { viewModelScope.launch { try { userRepository.updateStatus("online")  } catch (e: Exception) { } } }
    fun setUserOffline() { viewModelScope.launch { try { userRepository.updateStatus("offline") } catch (e: Exception) { } } }
}