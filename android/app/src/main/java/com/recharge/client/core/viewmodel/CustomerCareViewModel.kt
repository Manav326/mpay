package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.recharge.client.core.model.SupportTicketResponse
import com.recharge.client.core.model.SupportTicketSummaryResponse
import com.recharge.client.core.repository.CustomerCareRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class CustomerCareUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val tickets: List<SupportTicketSummaryResponse> = emptyList(),
    val selected: SupportTicketResponse? = null,
    val error: String? = null
)

class CustomerCareViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CustomerCareRepository.getInstance(application)
    private val _state = MutableStateFlow(CustomerCareUiState())
    val state: StateFlow<CustomerCareUiState> = _state

    fun load() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.tickets().onSuccess { page ->
                _state.value = _state.value.copy(loading = false, tickets = page.items)
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Unable to load customer-care cases.")
            }
        }
    }

    fun open(ticketId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.ticket(ticketId).onSuccess { ticket ->
                _state.value = _state.value.copy(loading = false, selected = ticket)
            }.onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.message ?: "Unable to load this support case.")
            }
        }
    }

    fun create(category: String, subject: String, message: String, onDone: () -> Unit = {}) {
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.create(category, subject.trim(), message.trim()).onSuccess { ticket ->
                _state.value = _state.value.copy(saving = false, selected = ticket)
                onDone()
            }.onFailure { error ->
                _state.value = _state.value.copy(saving = false, error = error.message ?: "Unable to create support case.")
            }
        }
    }

    fun reply(message: String) {
        val ticketId = _state.value.selected?.ticketId ?: return
        if (_state.value.saving || message.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.reply(ticketId, message.trim()).onSuccess { ticket ->
                _state.value = _state.value.copy(saving = false, selected = ticket)
            }.onFailure { error ->
                _state.value = _state.value.copy(saving = false, error = error.message ?: "Unable to send your reply.")
            }
        }
    }

    fun closeSelected() {
        val ticketId = _state.value.selected?.ticketId ?: return
        if (_state.value.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true, error = null)
            repository.close(ticketId).onSuccess { ticket ->
                _state.value = _state.value.copy(saving = false, selected = ticket)
                load()
            }.onFailure { error ->
                _state.value = _state.value.copy(saving = false, error = error.message ?: "Unable to close this support case.")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun clearSelected() {
        _state.value = _state.value.copy(selected = null, error = null)
    }
}
