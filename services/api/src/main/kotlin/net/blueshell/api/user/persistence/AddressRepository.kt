package net.blueshell.api.user.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/** An address with plaintext left and the member it belongs to, read past soft deletion. */
interface AddressPlaintext {
    val id: Long
    val userId: Long?
    val country: String?
    val city: String?
    val street: String?
    val houseNumber: String?
    val zipCode: String?
}

@Repository
interface AddressRepository : BaseRepository<Address, Long> {
    /** Every address with plaintext left, soft-deleted ones and their members included: what sealing still has to reach. */
    @Query(
        value = """
            SELECT a.id AS id, u.id AS userId, a.country AS country, a.city AS city, a.street AS street,
                   a.house_number AS houseNumber, a.zip_code AS zipCode
            FROM addresses a LEFT JOIN users u ON u.address_id = a.id
            WHERE a.country IS NOT NULL OR a.city IS NOT NULL OR a.street IS NOT NULL
               OR a.house_number IS NOT NULL OR a.zip_code IS NOT NULL
        """,
        nativeQuery = true,
    )
    fun findWithPlaintext(): List<AddressPlaintext>

    /** Writes [sealed] onto the address and empties its plaintext, soft-deleted or not. */
    @Modifying
    @Query(
        value = """
            UPDATE addresses
            SET sealed_address = :sealed, country = NULL, city = NULL, street = NULL, house_number = NULL, zip_code = NULL
            WHERE id = :id
        """,
        nativeQuery = true,
    )
    fun writeSealed(
        @Param("id") id: Long,
        @Param("sealed") sealed: String,
    ): Int
}
