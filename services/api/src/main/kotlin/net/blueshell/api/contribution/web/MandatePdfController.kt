package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.MandatePdfs
import net.blueshell.api.security.BoardOnly
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** An online mandate as a PDF, for the board. A member cannot download their own. */
@RestController
@Tag(name = "Memberships")
class MandatePdfController(
    private val pdfs: MandatePdfs,
    private val currentUser: CurrentUserProvider,
) {
    /** The membership's online mandate, filled in now and kept nowhere. Each download is written to the member's security log. */
    @BoardOnly
    @GetMapping("/memberships/{membershipId}/mandate/pdf", produces = [MediaType.APPLICATION_PDF_VALUE])
    @Operation(
        responses = [
            ApiResponse(
                responseCode = "200",
                content = [Content(mediaType = MediaType.APPLICATION_PDF_VALUE, schema = Schema(type = "string", format = "binary"))],
            ),
        ],
    )
    fun downloadMandatePdf(
        @PathVariable membershipId: Long,
    ): ResponseEntity<ByteArray> {
        val reader = currentUser.currentUser()?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val pdf = pdfs.pdf(membershipId, reader)
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
}
