package net.blueshell.api.declaration.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.declaration.domain.DeclarationPdfs
import net.blueshell.api.declaration.domain.DeclarationReviews
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** The board's review of submitted declarations. Nobody below board reads or moves one here. */
@RestController
@Tag(name = "Declarations")
@BoardOnly
class DeclarationReviewController(
    private val reviews: DeclarationReviews,
    private val pdfs: DeclarationPdfs,
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

    /** The submitter's account on file now, as the board transfers to it; the full IBAN opens only through a reveal. */
    @GetMapping("/declarations/{id}/bank-account")
    fun findDeclarationBankAccount(
        @PathVariable id: Long,
    ): DeclarationBankAccountResponse {
        val account = reviews.bankAccountOf(id)
        return DeclarationBankAccountResponse(
            account?.let { BankAccountToPayResponse(it.accountHolder, it.iban.country, it.iban.lastTwo, it.bic) },
        )
    }

    /** The board transferred the money by hand; the declaration keeps the day and the account it went to. */
    @PostMapping("/declarations/{id}/mark-paid")
    fun markDeclarationPaid(
        @PathVariable id: Long,
    ): DeclarationResponse = responses.of(reviews.markPaid(id, reader()))

    /** [ids] as one PDF, in the order given. */
    @GetMapping("/declarations/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @Operation(
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = Schema(type = "string", format = "binary"))],
            ),
        ],
    )
    fun downloadDeclarationsPdf(
        @RequestParam ids: List<Long>,
    ): ResponseEntity<ByteArray> {
        val pdf = pdfs.pdf(ids)
        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition
                    .attachment()
                    .filename(pdf.name)
                    .build()
                    .toString(),
            ).header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(pdf.bytes)
    }

    private fun reader(): Long = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
}
