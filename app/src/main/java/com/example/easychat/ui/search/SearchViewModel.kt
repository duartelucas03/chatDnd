package com.example.easychat.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.easychat.data.repository.ChatRepository
import com.example.easychat.data.repository.UserRepository
import com.example.easychat.model.ChatroomModel
import com.example.easychat.model.UserModel
import com.example.easychat.utils.SupabaseClientProvider
import kotlinx.coroutines.launch

class SearchViewModel(
    private val userRepository: UserRepository = UserRepository(),
    private val chatRepository: ChatRepository = ChatRepository()
) : ViewModel() {

    private val _results = MutableLiveData<List<UserModel>>(emptyList())
    val results: LiveData<List<UserModel>> = _results

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> = _loading

    private val _groupCreated = MutableLiveData<ChatroomModel?>(null)
    val groupCreated: LiveData<ChatroomModel?> = _groupCreated

    private val _error = MutableLiveData<String?>(null)
    val error: LiveData<String?> = _error

    fun searchUsers(term: String) {
        if (term.length < 3) return
        _loading.value = true
        viewModelScope.launch {
            val users = userRepository.searchUsers(term)
            _results.postValue(users)
            _loading.postValue(false)
        }
    }

    /** Cruza lista de números de telefone (contatos do celular) com usuários cadastrados */
    fun searchByPhones(phones: List<String>) {
        if (phones.isEmpty()) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val users = userRepository.getUsersByPhones(phones)
                _results.postValue(users)
            } catch (e: Exception) {
                _error.postValue("Erro ao buscar contatos: ${e.message}")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun createGroup(name: String, members: List<UserModel>) {
        if (name.isBlank() || members.isEmpty()) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val creatorId = SupabaseClientProvider.currentUserId()
                val room = chatRepository.createGroupChatroom(
                    name      = name,
                    memberIds = members.map { it.id },
                    creatorId = creatorId
                )
                _groupCreated.postValue(room)
            } catch (e: Exception) {
                _error.postValue("Erro ao criar grupo: ${e.message}")
            } finally {
                _loading.postValue(false)
            }
        }
    }

    fun clearGroupCreated() { _groupCreated.value = null }
    fun clearError() { _error.value = null }
}
