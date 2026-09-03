package net.blueshell.api.contribution.domain

import net.blueshell.api.platform.config.BankProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The ways the association can be paid, as an email needs to describe them.
 *
 * The bank details and the site's address travel together through every payment email —
 * an email that names an account also names where to find the mandate for letting us
 * collect automatically — so they arrive as one thing rather than as two parameters each
 * builder has to be handed separately.
 */
@Component
data class PaymentChannels(
    val bank: BankProperties,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) {
    /**
     * Where the direct debit mandate is published.
     *
     * The documents page rather than the file: asset filenames carry a content hash, so no
     * url of the pdf survives a revision of it and a link in an email already delivered
     * would die.
     */
    val documentsUrl: String get() = "$frontendUrl/documents"
}
