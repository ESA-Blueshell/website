package net.blueshell.api.email.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.email.domain.SendingAddressChange
import net.blueshell.api.email.domain.SendingAddressView
import net.blueshell.api.email.domain.SendingAddresses
import net.blueshell.api.email.persistence.MailSecurity
import net.blueshell.api.security.BoardOnly
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Schema(name = "SendingAddressRequest", description = "An address and, when it is new, its servers move or its login changes, the login")
data class SendingAddressRequest(
    @field:NotBlank @field:Email @field:Size(max = 320)
    val address: String,
    @field:NotBlank @field:Size(max = 128)
    val displayName: String,
    @field:NotBlank @field:Size(max = 255)
    val host: String,
    @field:Min(1) @field:Max(65535)
    val port: Int,
    val security: MailSecurity,
    val isDefault: Boolean = false,
    @param:Schema(description = "The username for SMTP and IMAP alike; left out to keep the login there is")
    val username: String? = null,
    @param:Schema(description = "The password, written to Vault and never answered back; left out to keep the one there is")
    val password: String? = null,
    @field:Size(max = 255)
    @param:Schema(description = "Its IMAP server; left out where the address is not read")
    val imapHost: String? = null,
    @field:Min(1) @field:Max(65535)
    val imapPort: Int? = null,
    val imapSecurity: MailSecurity? = null,
)

private fun SendingAddressRequest.change() =
    SendingAddressChange(address, displayName, host, port, security, isDefault, username, password, imapHost, imapPort, imapSecurity)

/** The addresses the site sends from and reads, kept by the board; the default one sends the site's own mail. */
@RestController
@RequestMapping("/management/sending-addresses")
@Tag(name = "Sending addresses", description = "The addresses the site sends from and reads, each with its own login in Vault")
class SendingAddressController(
    private val addresses: SendingAddresses,
) {
    @GetMapping
    @BoardOnly
    fun listSendingAddresses(): List<SendingAddressView> = addresses.list()

    @PostMapping
    @BoardOnly
    @ResponseStatus(HttpStatus.CREATED)
    fun addSendingAddress(
        @Valid @RequestBody request: SendingAddressRequest,
    ): SendingAddressView = addresses.add(request.change())

    @PutMapping("/{id}")
    @BoardOnly
    fun setSendingAddress(
        @PathVariable id: Long,
        @Valid @RequestBody request: SendingAddressRequest,
    ): SendingAddressView = addresses.update(id, request.change())

    /** Tries the address's servers with the login kept, and answers what they said. */
    @PostMapping("/{id}/check")
    @BoardOnly
    fun checkSendingAddress(
        @PathVariable id: Long,
    ): SendingAddressView = addresses.check(id)

    @DeleteMapping("/{id}")
    @BoardOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeSendingAddress(
        @PathVariable id: Long,
    ) = addresses.remove(id)
}
