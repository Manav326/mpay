package com.recharge.backend.api

import com.recharge.backend.service.ClientCommissionService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/commission")
class ClientCommissionController(
    private val clientCommissionService: ClientCommissionService
) {
    private fun userId(authentication: Authentication): Long =
        authentication.name.toLongOrNull() ?: throw IllegalStateException("Invalid authenticated user")

    @GetMapping("/overview")
    fun overview(authentication: Authentication): ClientCommissionOverviewResponse =
        clientCommissionService.overview(userId(authentication))

    @GetMapping("/clients")
    fun directClients(authentication: Authentication): List<ClientReferralMemberResponse> =
        clientCommissionService.directClients(userId(authentication))

    @GetMapping("/clients/search")
    fun searchClients(
        authentication: Authentication,
        @RequestParam q: String
    ): List<ClientSearchResultResponse> =
        clientCommissionService.searchEligibleClients(userId(authentication), q)

    @PostMapping("/clients")
    fun addClient(
        authentication: Authentication,
        @Valid @RequestBody request: AddClientCommissionMemberRequest
    ): ClientReferralMemberResponse =
        clientCommissionService.addClient(userId(authentication), request.clientPublicId)

    @GetMapping("/upstream-history")
    fun upstreamHistory(
        authentication: Authentication,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ClientUpstreamCommissionPageResponse =
        clientCommissionService.upstreamHistory(userId(authentication), page, size)
}
