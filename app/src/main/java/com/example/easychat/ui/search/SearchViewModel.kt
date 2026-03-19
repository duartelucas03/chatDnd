// ─────────────────────────────────────────────────────────────────────────────
// ui/search/SearchViewModel.kt
// ─────────────────────────────────────────────────────────────────────────────
package com.example.easychat.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.ChatRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.ChatroomModel
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _results      = MutableStateFlow<List<UserModel>>(emptyList())
    val results: StateFlow<List<UserModel>> = _results.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _groupCreated = MutableStateFlow<ChatroomModel?>(null)
    val groupCreated: StateFlow<ChatroomModel?> = _groupCreated.asStateFlow()

    private val _error        = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun searchUsers(term: String) {
        if (term.length < 3) return
        _loading.value = true
        viewModelScope.launch {
            _results.value = userRepository.searchUsers(term)
            _loading.value = false
        }
    }

    fun searchByPhones(phones: List<String>) {
        if (phones.isEmpty()) return
        _loading.value = true
        viewModelScope.launch {
            try {
                _results.value = userRepository.getUsersByPhones(phones)
            } catch (e: Exception) {
                _error.value = "Erro ao buscar contatos: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun createGroup(name: String, members: List<UserModel>) {
        if (name.isBlank() || members.isEmpty()) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val room = chatRepository.createGroupChatroom(
                    name      = name,
                    memberIds = members.map { it.id },
                    creatorId = SupabaseClientProvider.currentUserId()
                )
                _groupCreated.value = room
            } catch (e: Exception) {
                _error.value = "Erro ao criar grupo: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun clearGroupCreated() { _groupCreated.value = null }
    fun clearError()        { _error.value = null }
}