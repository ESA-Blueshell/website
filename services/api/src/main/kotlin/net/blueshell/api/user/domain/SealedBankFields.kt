package net.blueshell.api.user.domain

import net.blueshell.api.user.api.SealedField
import net.blueshell.api.user.api.SealedValue
import net.blueshell.api.user.domain.sealing.sealingContext
import net.blueshell.api.user.persistence.SealedAccountRow
import net.blueshell.api.user.persistence.SealedMandateRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The three sealed bank columns, each registered with the nightly rewrap: the IBAN, the account
 * holder and an online mandate's address. A mandate no longer collected from keeps them until it is
 * wiped, so those rows are moved too.
 */
@Configuration
class SealedBankFields(
    private val mandates: SealedMandateRepository,
    private val sealing: SealedBankDetails,
) {
    @Bean
    fun mandateIbans(): SealedField =
        column("mandate IBAN", SealedBankDetails.IBAN, mandates::findSealedMandates, SealedAccountRow::iban, mandates::swapSealedIban)

    @Bean
    fun mandateAccountHolders(): SealedField =
        column(
            "mandate account holder",
            SealedBankDetails.ACCOUNT_HOLDER,
            mandates::findSealedMandates,
            SealedAccountRow::accountHolder,
            mandates::swapSealedAccountHolder,
        )

    @Bean
    fun mandateAddresses(): SealedField =
        column(
            "mandate address",
            SealedBankDetails.ADDRESS,
            mandates::findSealedMandates,
            SealedAccountRow::address,
            mandates::swapSealedAddress,
        )

    // A row without the value, a paper mandate's address, has nothing to move.
    private fun column(
        name: String,
        field: String,
        rows: () -> List<SealedAccountRow>,
        value: (SealedAccountRow) -> String?,
        swap: (Long, String, String) -> Int,
    ): SealedField =
        object : SealedField {
            override val name = name
            override val key = sealing.key

            override fun sealedValues() =
                rows().mapNotNull { row -> value(row)?.let { SealedValue(row.id, it, sealingContext(field, row.userId)) } }

            override fun swap(
                id: Long,
                was: String,
                sealed: String,
            ) = swap(id, was, sealed) == 1
        }
}
