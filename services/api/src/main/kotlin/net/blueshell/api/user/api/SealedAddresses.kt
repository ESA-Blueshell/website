package net.blueshell.api.user.api

import net.blueshell.api.user.domain.sealing.Sealed
import net.blueshell.api.user.domain.sealing.SealedValueUnopenable
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.domain.sealing.sealingContext
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.AddressRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.ObjectMapper

/** An address in the clear, as one person's view shows it. */
data class AddressFields(
    val country: String?,
    val city: String?,
    val street: String?,
    val houseNumber: String?,
    val zipCode: String?,
)

/**
 * A member's address, sealed through Vault Transit under `privacy.address-key` as one value bound
 * to the member (api ADR **Private details are sealed by Vault Transit**). Saving is refused with
 * [SealingUnavailable] when the key cannot be reached; viewing then shows no address. Nothing
 * falls back to plaintext. An address the sealing job has not reached yet still reads its
 * plaintext, which the job then seals.
 */
@Service
class SealedAddresses(
    private val sealer: Sealer,
    private val addresses: AddressRepository,
    private val mapper: ObjectMapper,
    private val transactions: TransactionTemplate,
    @param:Value($$"${privacy.address-key:api-address}") private val key: String,
) {
    /** Seals [fields] onto [address] and empties its plaintext. */
    fun seal(
        address: Address,
        fields: AddressFields,
    ) {
        val userId = requireNotNull(address.user.id) { "An address is sealed to a member with an id" }
        address.sealed = sealer.seal(key, listOf(Sealed(mapper.writeValueAsString(fields), sealingContext(FIELD, userId)))).single()
        address.country = null
        address.city = null
        address.street = null
        address.houseNumber = null
        address.zipCode = null
    }

    /** The address in the clear, for one person's view, or null where it cannot be opened now. */
    fun open(address: Address): AddressFields? {
        val sealed =
            address.sealed ?: return AddressFields(address.country, address.city, address.street, address.houseNumber, address.zipCode)
        val userId = requireNotNull(address.user.id)
        return try {
            mapper.readValue(sealer.open(key, listOf(Sealed(sealed, sealingContext(FIELD, userId)))).single(), AddressFields::class.java)
        } catch (away: SealingUnavailable) {
            log.warn("[privacy] address {} cannot be opened now: {}", address.id, away.message)
            null
        } catch (refused: SealedValueUnopenable) {
            log.warn("[privacy] address {} does not open under its member", address.id, refused)
            null
        }
    }

    /**
     * Seals every address with plaintext left, soft-deleted ones included, and answers how many it
     * sealed. An address both sealed and written in plaintext since, by a release that did not
     * seal, takes the plaintext: it is the newer write. An address no member holds has nobody to
     * be bound to, so it is left for an admin and logged.
     */
    fun sealEvery(): Int =
        addresses.findWithPlaintext().count { row ->
            val userId = row.userId
            if (userId == null) {
                log.warn("[privacy] address {} belongs to no member, so it is left unsealed", row.id)
                return@count false
            }
            val fields = AddressFields(row.country, row.city, row.street, row.houseNumber, row.zipCode)
            val sealed = sealer.seal(key, listOf(Sealed(mapper.writeValueAsString(fields), sealingContext(FIELD, userId)))).single()
            transactions.execute { addresses.writeSealed(row.id, sealed) } == 1
        }

    private companion object {
        const val FIELD = "address"
        val log = LoggerFactory.getLogger(SealedAddresses::class.java)
    }
}
