package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The account members transfer their contribution to, quoted in the payment request.
 *
 * Configured under `blueshell.bank.*` rather than written into the email builder, so a
 * change of bank is a deploy and not a code change.
 */
@ConfigurationProperties(prefix = "blueshell.bank")
data class BankProperties(
    val iban: String = "NL19 INGB 0008 0964 62",
    val bic: String = "INGBNL2A",
    val accountName: String = "Blueshell E-Sports Vereniging",
)
