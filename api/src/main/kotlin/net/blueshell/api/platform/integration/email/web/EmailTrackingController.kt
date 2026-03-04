package net.blueshell.api.platform.integration.email.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.shared.enums.EmailDeliveryStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Public endpoint for email open tracking via a 1×1 transparent GIF (tracking pixel).
 *
 * Each outbound email contains an <img> tag pointing to /track/email/open/{token}.
 * When an email client loads remote images, this endpoint fires, recording:
 *   - DELIVERED (inferred — if the pixel loaded, the email reached the client)
 *   - OPENED    (the recipient or client loaded the message body)
 *
 * The response is always a 1×1 transparent GIF so behaviour is invisible to the reader.
 * No authentication is required; the token is an opaque UUID per email.
 *
 * Limitations:
 *   - Email clients that block remote images will not trigger this endpoint.
 *   - iOS Mail Privacy Protection (and similar) pre-fetches pixels server-side,
 *     so the open may be recorded even if the user never actually reads the message.
 *   - These are industry-standard trade-offs for any pixel-based tracking.
 */
@Hidden
@Tag(name = "Email Tracking")
@RestController
@RequestMapping("/track/email")
class EmailTrackingController(
    private val emailService: EmailService,
) {
    @PermitAll
    @GetMapping("/open/{token}")
    fun trackOpen(@PathVariable token: String): ResponseEntity<ByteArray> {
        runCatching {
            val outbox = emailService.findByTrackingToken(token)
            if (outbox != null) {
                when (outbox.deliveryStatus) {
                    EmailDeliveryStatus.SENT,
                    EmailDeliveryStatus.DELIVERED -> emailService.markOpened(outbox)
                    else -> { /* already opened, bounced, or failed — no state change */ }
                }
                log.debug("Tracking pixel fired for outbox id={} token={}", outbox.id, token)
            } else {
                log.warn("Tracking pixel fired for unknown token={}", token)
            }
        }.onFailure { log.warn("Error recording email open for token={}", token, it) }

        val headers = HttpHeaders()
        headers.contentType = MediaType.IMAGE_GIF
        headers.cacheControl = org.springframework.http.CacheControl.noStore().headerValue
        headers.pragma = "no-cache"
        return ResponseEntity(TRANSPARENT_GIF, headers, HttpStatus.OK)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EmailTrackingController::class.java)

        /**
         * 1×1 transparent GIF (35 bytes).
         * Standard minimal GIF89a: width=1, height=1, 0% opacity.
         */
        private val TRANSPARENT_GIF = byteArrayOf(
            0x47, 0x49, 0x46, 0x38, 0x39, 0x61,  // GIF89a
            0x01, 0x00, 0x01, 0x00,               // width=1, height=1
            0x80.toByte(), 0x00, 0x00,            // global color table flag, 2 colors
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), // color 0: white
            0x00, 0x00, 0x00,                     // color 1: black
            0x21, 0xF9.toByte(), 0x04, 0x01,      // graphic control extension
            0x00, 0x00, 0x00, 0x00,               // delay=0, transparent index=0
            0x2C,                                  // image descriptor
            0x00, 0x00, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0x02, 0x02, 0x44, 0x01, 0x00,         // image data
            0x3B                                   // GIF trailer
        )
    }
}
