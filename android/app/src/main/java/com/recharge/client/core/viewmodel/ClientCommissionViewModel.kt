package com.recharge.client.core.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.recharge.client.core.model.ClientCommissionOverviewResponse
import com.recharge.client.core.model.ClientReferralMemberResponse
import com.recharge.client.core.model.ClientSearchResultResponse
import com.recharge.client.core.model.ClientUpstreamCommissionHistoryItem
import com.recharge.client.core.repository.ClientRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ClientCommissionUiState(
    val overview: ClientCommissionOverviewResponse? = null,
    val clients: List<ClientReferralMemberResponse> = emptyList(),
    val upstreamHistory: List<ClientUpstreamCommissionHistoryItem> = emptyList(),
    val upstreamPage: Int = 0,
    val upstreamTotalItems: Long = 0,
    val upstreamTotalPages: Int = 0,
    val upstreamHasNext: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<ClientSearchResultResponse> = emptyList(),
    val loading: Boolean = false,
    val loadingHistory: Boolean = false,
    val searching: Boolean = false,
    val addingClientId: String? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
    val searchError: String? = null,
    val searchMessage: String? = null,
    val actionMessage: String? = null
)

class ClientCommissionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ClientRepository.getInstance(application)
    private val _state = MutableStateFlow(ClientCommissionUiState())
    val state = _state.asStateFlow()

    private var loadJob: Job? = null
    private var historyJob: Job? = null
    private var searchJob: Job? = null

    fun load() {
        if (_state.value.loading || _state.value.refreshing) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val initial = _state.value
            _state.value = initial.copy(
                loading = initial.overview == null,
                refreshing = initial.overview != null,
                error = null,
                actionMessage = null
            )
            val overview = repository.clientCommissionOverview()
            if (overview.isFailure) {
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = overview.exceptionOrNull()?.message ?: "Unable to load client network."
                )
                return@launch
            }
            val clients = repository.clientCommissionClients()
            _state.value = _state.value.copy(
                overview = overview.getOrThrow(),
                clients = clients.getOrDefault(emptyList()),
                loading = false,
                refreshing = false,
                error = clients.exceptionOrNull()?.message
            )
            loadHistory(0)
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _state.value = _state.value.copy(refreshing = false)
        load()
    }

    fun setSearchQuery(value: String) {
        _state.value = _state.value.copy(
            searchQuery = value.take(80),
            searchResults = emptyList(),
            searchError = null,
            searchMessage = null
        )
    }

    fun searchClients() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) {
            _state.value = _state.value.copy(
                searchResults = emptyList(),
                searchError = null,
                searchMessage = "Enter a Client ID."
            )
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(searching = true, searchError = null, searchMessage = null)
            repository.searchCommissionClients(query)
                .onSuccess { results ->
                    _state.value = _state.value.copy(
                        searching = false,
                        searchResults = results,
                        searchError = null,
                        searchMessage = if (results.isEmpty()) {
                            "No client account was found for that Client ID. Check the ID and try again."
                        } else null
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        searching = false,
                        searchResults = emptyList(),
                        searchError = error.message ?: "Unable to search clients.",
                        searchMessage = null
                    )
                }
        }
    }

    fun addClient(publicId: String) {
        if (_state.value.addingClientId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                addingClientId = publicId,
                searchError = null,
                actionMessage = null
            )
            repository.addCommissionClient(publicId)
                .onSuccess {
                    _state.value = _state.value.copy(
                        addingClientId = null,
                        searchResults = _state.value.searchResults.filterNot { it.publicUserId == publicId },
                        searchError = null,
                        searchMessage = null,
                        actionMessage = "Client added to your network."
                    )
                    load()
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        addingClientId = null,
                        searchError = error.message ?: "Unable to add this client."
                    )
                }
        }
    }

    fun loadHistory(page: Int = _state.value.upstreamPage) {
        if (_state.value.loadingHistory) return
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            _state.value = _state.value.copy(loadingHistory = true)
            repository.upstreamCommissionHistory(page = page, size = 20)
                .onSuccess { response ->
                    _state.value = _state.value.copy(
                        loadingHistory = false,
                        upstreamHistory = response.items,
                        upstreamPage = response.page,
                        upstreamTotalItems = response.totalItems,
                        upstreamTotalPages = response.totalPages,
                        upstreamHasNext = response.hasNext
                    )
                }
                .onFailure { error ->
                    _state.value = _state.value.copy(
                        loadingHistory = false,
                        error = error.message ?: "Unable to load upstream earnings."
                    )
                }
        }
    }

    fun previousHistoryPage() {
        val page = (_state.value.upstreamPage - 1).coerceAtLeast(0)
        if (page != _state.value.upstreamPage) loadHistory(page)
    }

    fun nextHistoryPage() {
        if (_state.value.upstreamHasNext) loadHistory(_state.value.upstreamPage + 1)
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, searchError = null, actionMessage = null)
    }

    fun resetSession() {
        loadJob?.cancel()
        historyJob?.cancel()
        searchJob?.cancel()
        _state.value = ClientCommissionUiState()
    }
}
