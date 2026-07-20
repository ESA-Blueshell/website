package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Blueshell bank account details used in contribution emails so that members can
 * transfer their membership fee. Configured via `blueshell.bank.*` so the values
 * live in configuration rather than being hardcoded in the email builders.
 */
@ConfigurationProperties(prefix = "blueshell.bank")
data class BankProperties(
    val iban: String = "NL19 INGB 0008 0964 62",
    val bic: String = "INGBNL2A",
    val accountName: String = "Blueshell E-Sports Vereniging",
)
