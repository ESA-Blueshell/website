package net.blueshell.api.declaration.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.declaration.domain.DeclarationReviews
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** The board's review of submitted declarations. Nobody below board reads or moves one here. */
@RestController
@Tag(name = "Declarations")
@BoardOnly
class DeclarationReviewController(
    private val reviews: DeclarationReviews,
    private val responses: DeclarationResponses,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping("/declarations")
    fun listDeclarations(): List<DeclarationResponse> = responses.of(reviews.all())

    @GetMapping("/declarations/{id}")
    fun findDeclaration(
        @PathVariable id: Long,
    ): DeclarationResponse = responses.of(reviews.one(id))

    @PostMapping("/declarations/{id}/approve")
    fun approveDeclaration(
        @PathVariable id: Long,
    ): DeclarationResponse = responses.of(reviews.approve(id, reader()))

    @PostMapping("/declarations/{id}/send-back")
    fun sendBackDeclaration(
        @PathVariable id: Long,
        @Valid @RequestBody request: DeclarationReasonRequest,
    ): DeclarationResponse = responses.of(reviews.sendBack(id, reader(), request.reason))

    @PostMapping("/declarations/{id}/reject")
    fun rejectDeclaration(
        @PathVariable id: Long,
        @Valid @RequestBody request: DeclarationReasonRequest,
    ): DeclarationResponse = responses.of(reviews.reject(id, reader(), request.reason))

    private fun reader(): Long = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
}
