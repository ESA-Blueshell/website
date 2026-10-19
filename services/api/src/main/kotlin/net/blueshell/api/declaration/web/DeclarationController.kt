package net.blueshell.api.declaration.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.declaration.domain.Declarations
import net.blueshell.api.declaration.domain.Receipts
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.core.io.InputStreamResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.TimeUnit

private const val RECEIPT_CACHE_HOURS = 1L

/** The reader's own declarations, and the receipts on them. Anybody with an account declares. */
@RestController
@Tag(name = "Declarations")
@PreAuthorize("isAuthenticated()")
class DeclarationController(
    private val declarations: Declarations,
    private val receipts: Receipts,
    private val responses: DeclarationResponses,
    private val currentUser: CurrentUserProvider,
) {
    @GetMapping("/users/me/declarations")
    fun listOwnDeclarations(): List<DeclarationResponse> = responses.of(declarations.own(reader().id))

    @GetMapping("/users/me/declarations/{id}")
    fun findOwnDeclaration(
        @PathVariable id: Long,
    ): DeclarationResponse = responses.of(declarations.ownOne(reader().id, id))

    @PostMapping("/users/me/declarations")
    @ResponseStatus(HttpStatus.CREATED)
    fun startDeclaration(
        @Valid @RequestBody request: DeclarationDraftRequest,
    ): DeclarationResponse = responses.of(declarations.start(reader().id, request.asContent()))

    @PutMapping("/users/me/declarations/{id}")
    fun saveDeclarationDraft(
        @PathVariable id: Long,
        @Valid @RequestBody request: DeclarationDraftRequest,
    ): DeclarationResponse = responses.of(declarations.save(reader().id, id, request.asContent()))

    @DeleteMapping("/users/me/declarations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteDeclarationDraft(
        @PathVariable id: Long,
    ) = declarations.delete(reader().id, id)

    @PostMapping("/users/me/declarations/{id}/submit")
    fun submitDeclaration(
        @PathVariable id: Long,
    ): DeclarationResponse = responses.of(declarations.submit(reader().id, id))

    @PostMapping("/users/me/declarations/{id}/withdraw")
    fun withdrawDeclaration(
        @PathVariable id: Long,
    ): DeclarationResponse = responses.of(declarations.withdraw(reader().id, id))

    /** Stored before the draft names it, as the picture inputs store a picture before a save. */
    @PostMapping(value = ["/declarations/receipts"], consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadReceipt(
        @RequestPart("file") file: MultipartFile,
    ): ReceiptResponse = receipts.store(file).let { it.asReceipt(it.id!!) }

    /**
     * A receipt drawn inline: a photo in an `img`, a PDF in an `object` on the site's own origin,
     * which `SecurityConfig` lets frame this route alone.
     * Anybody else is told it does not exist.
     */
    @GetMapping("/declarations/receipts/{id}")
    fun openReceipt(
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        val file = receipts.open(reader(), id)
        val answer =
            ResponseEntity
                .ok()
                .contentType(MediaType.parseMediaType(file.mediaType))
                .cacheControl(CacheControl.maxAge(RECEIPT_CACHE_HOURS, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(file.name))
                .header("X-Content-Type-Options", "nosniff")
        if (file.mediaType.startsWith("image/")) answer.header("Content-Security-Policy", IMAGE_POLICY)
        return answer.body(InputStreamResource(receipts.contentOf(file)))
    }

    private fun disposition(name: String): String =
        ContentDisposition
            .inline()
            .filename(name)
            .build()
            .toString()

    private fun reader(): CurrentUser = currentUser.currentUser() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)

    private companion object {
        // As the public files have it: a picture runs no script, whatever an upload check missed.
        const val IMAGE_POLICY = "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; sandbox"
    }
}
