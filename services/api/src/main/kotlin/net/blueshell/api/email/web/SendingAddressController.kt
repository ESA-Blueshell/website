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
import net.blueshell.api.email.persistence.SmtpSecurity
import net.blueshell.api.security.AdminOnly
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

@Schema(name = "SendingAddressRequest", description = "A sending address and, when it is new or its login changes, the login")
data class SendingAddressRequest(
    @field:NotBlank @field:Email @field:Size(max = 320)
    val address: String,
    @field:NotBlank @field:Size(max = 128)
    val displayName: String,
    @field:NotBlank @field:Size(max = 255)
    val host: String,
    @field:Min(1) @field:Max(65535)
    val port: Int,
    val security: SmtpSecurity,
    val isDefault: Boolean = false,
    @param:Schema(description = "The SMTP username; left out to keep the login there is")
    val username: String? = null,
    @param:Schema(description = "The SMTP password, written to Vault and never answered back; left out to keep the one there is")
    val password: String? = null,
) {
    fun change() = SendingAddressChange(address, displayName, host, port, security, isDefault, username, password)
}

/** The addresses written emails may be sent from. The board reads them to pick one; an admin keeps them. */
@RestController
@RequestMapping("/management/sending-addresses")
@Tag(name = "Sending addresses", description = "The addresses written emails go out from, each with its own SMTP login in Vault")
class SendingAddressController(
    private val addresses: SendingAddresses,
) {
    @GetMapping
    @BoardOnly
    fun listSendingAddresses(): List<SendingAddressView> = addresses.list()

    @PostMapping
    @AdminOnly
    @ResponseStatus(HttpStatus.CREATED)
    fun addSendingAddress(
        @Valid @RequestBody request: SendingAddressRequest,
    ): SendingAddressView = addresses.add(request.change())

    @PutMapping("/{id}")
    @AdminOnly
    fun setSendingAddress(
        @PathVariable id: Long,
        @Valid @RequestBody request: SendingAddressRequest,
    ): SendingAddressView = addresses.update(id, request.change())

    @DeleteMapping("/{id}")
    @AdminOnly
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeSendingAddress(
        @PathVariable id: Long,
    ) = addresses.remove(id)
}
