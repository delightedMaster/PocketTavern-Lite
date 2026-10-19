package me.rerere.rikkahub.ui.pages.extensions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.GroupChat
import me.rerere.rikkahub.service.ChatService
import kotlin.uuid.Uuid

class GroupChatsVM(
    private val settingsStore: SettingsStore,
    private val chatService: ChatService,
) : ViewModel() {
    val settings = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings.dummy())

    fun save(group: GroupChat) {
        viewModelScope.launch {
            settingsStore.update { current ->
                val exists = current.groupChats.any { it.id == group.id }
                current.copy(
                    groupChats = if (exists) {
                        current.groupChats.map { if (it.id == group.id) group else it }
                    } else {
                        current.groupChats + group
                    },
                )
            }
        }
    }

    fun delete(id: Uuid) {
        viewModelScope.launch {
            settingsStore.update { current ->
                current.copy(groupChats = current.groupChats.filterNot { it.id == id })
            }
        }
    }

    fun open(id: Uuid, onOpened: (Uuid) -> Unit) {
        viewModelScope.launch {
            val conversationId = chatService.openGroupChat(id)
            onOpened(conversationId)
        }
    }
}
