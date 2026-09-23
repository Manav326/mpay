package com.recharge.backend.api

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import jakarta.validation.ConstraintViolationException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.servlet.http.HttpServletRequest

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentials(ex: BadCredentialsException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ex.message ?: "Invalid mobile number or password"))

    @ExceptionHandler(com.recharge.backend.provider.Way2ApiException::class)
    fun handleWay2Api(ex: com.recharge.backend.provider.Way2ApiException): ResponseEntity<ErrorResponse> {
        val status = when (ex.upstreamStatusCode) {
            400 -> HttpStatus.BAD_REQUEST
            401 -> HttpStatus.UNAUTHORIZED
            402 -> HttpStatus.PAYMENT_REQUIRED
            403 -> HttpStatus.FORBIDDEN
            404 -> HttpStatus.BAD_GATEWAY
            422 -> HttpStatus.UNPROCESSABLE_ENTITY
            429 -> HttpStatus.TOO_MANY_REQUESTS
            202 -> HttpStatus.ACCEPTED
            503 -> HttpStatus.SERVICE_UNAVAILABLE
            null -> HttpStatus.GATEWAY_TIMEOUT
            else -> HttpStatus.BAD_GATEWAY
        }
        return ResponseEntity.status(status).body(ErrorResponse(ex.message ?: "Way2API request failed"))
    }

    @ExceptionHandler(com.recharge.backend.service.ProviderNotConfiguredException::class)
    fun handleProviderNotConfigured(ex: com.recharge.backend.service.ProviderNotConfiguredException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(ex.message ?: "Requested provider is not configured"))

    @ExceptionHandler(com.recharge.backend.service.OtpDeliveryException::class)
    fun handleOtpDelivery(ex: com.recharge.backend.service.OtpDeliveryException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse(ex.message ?: "OTP delivery service is currently unavailable"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Bad request"))

    @ExceptionHandler(IllegalStateException::class)
    fun handleState(ex: IllegalStateException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ex.message ?: "Unauthorized"))

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException::class)
    fun handleAccessDenied(ex: org.springframework.security.access.AccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ex.message ?: "Access denied"))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableRequest(ex: HttpMessageNotReadableException, request: HttpServletRequest): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("Invalid request body for ${request.method} ${request.requestURI}: " + (ex.mostSpecificCause.message ?: "malformed JSON")))

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    ex.constraintViolations.joinToString("; ") {
                        "${it.propertyPath}: ${it.message}"
                    }
                )
            )
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString("; ") {
            "${it.field}: ${it.defaultMessage ?: "invalid value"}"
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }
}

data class ErrorResponse(val message: String)
