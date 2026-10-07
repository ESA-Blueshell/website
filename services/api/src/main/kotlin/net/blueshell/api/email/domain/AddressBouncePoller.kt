package net.blueshell.api.email.domain

import jakarta.mail.Folder
import net.blueshell.api.email.api.AddressMailboxes
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/** Takes the delivery reports that land in each sending address's own mailbox, the one its mail went out from. */
@Service
class AddressBouncePoller(
    private val mailboxes: AddressMailboxes,
    private val emailService: EmailService,
) {
    @Scheduled(fixedDelayString = "\${email.bounce.poll-interval-ms:300000}")
    fun poll() = mailboxes.eachOpen(Folder.READ_WRITE) { _, folder -> takeBounces(emailService, folder) }
}
