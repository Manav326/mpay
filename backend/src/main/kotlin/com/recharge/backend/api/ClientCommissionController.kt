package com.recharge.backend.api

import com.recharge.backend.service.ClientCommissionService
import com.recharge.backend.service.ImageVariant
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.security.core.Authentication
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Duration

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

    @GetMapping("/clients/{publicId}/profile-image")
    fun clientProfileImage(
        authentication: Authentication,
        @PathVariable publicId: String,
        @RequestParam(required = false) variant: String?
    ): ResponseEntity<org.springframework.core.io.Resource> {
        val selectedVariant = ImageVariant.parse(variant)
        val stored = clientCommissionService.clientProfileImage(userId(authentication), publicId, selectedVariant)
        val etag = stored.key + ":" + selectedVariant.name + ":" + stored.lastModified.toEpochMilli() + ":" + stored.size
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(stored.contentType))
            .contentLength(stored.size)
            .lastModified(stored.lastModified.toEpochMilli())
            .eTag(etag)
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
            .body(stored.resource)
    }

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
