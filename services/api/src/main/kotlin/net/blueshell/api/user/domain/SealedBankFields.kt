package net.blueshell.api.user.domain

import net.blueshell.api.user.api.SealedField
import net.blueshell.api.user.api.SealedValue
import net.blueshell.api.user.domain.sealing.sealingContext
import net.blueshell.api.user.persistence.PendingMandateRepository
import net.blueshell.api.user.persistence.SealedAccountRow
import net.blueshell.api.user.persistence.SealedMandateRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * The six sealed bank columns, each registered with the nightly rewrap: the IBAN, the account
 * holder and an online mandate's address, on a membership's mandate and on a pending one. An ended
 * or soft-deleted membership keeps its mandate until it is wiped, so those rows are moved too.
 */
@Configuration
class SealedBankFields(
    private val memberships: SealedMandateRepository,
    private val pending: PendingMandateRepository,
    private val sealing: SealedBankDetails,
) {
    @Bean
    fun mandateIbans(): SealedField =
        column("mandate IBAN", SealedBankDetails.IBAN, memberships::findSealedMandates, SealedAccountRow::iban, memberships::swapSealedIban)

    @Bean
    fun mandateAccountHolders(): SealedField =
        column(
            "mandate account holder",
            SealedBankDetails.ACCOUNT_HOLDER,
            memberships::findSealedMandates,
            SealedAccountRow::accountHolder,
            memberships::swapSealedAccountHolder,
        )

    @Bean
    fun pendingMandateIbans(): SealedField =
        column("pending mandate IBAN", SealedBankDetails.IBAN, pending::findSealed, SealedAccountRow::iban, pending::swapSealedIban)

    @Bean
    fun pendingMandateAccountHolders(): SealedField =
        column(
            "pending mandate account holder",
            SealedBankDetails.ACCOUNT_HOLDER,
            pending::findSealed,
            SealedAccountRow::accountHolder,
            pending::swapSealedAccountHolder,
        )

    @Bean
    fun mandateAddresses(): SealedField =
        column(
            "mandate address",
            SealedBankDetails.ADDRESS,
            memberships::findSealedMandates,
            SealedAccountRow::address,
            memberships::swapSealedAddress,
        )

    @Bean
    fun pendingMandateAddresses(): SealedField =
        column(
            "pending mandate address",
            SealedBankDetails.ADDRESS,
            pending::findSealed,
            SealedAccountRow::address,
            pending::swapSealedAddress,
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
