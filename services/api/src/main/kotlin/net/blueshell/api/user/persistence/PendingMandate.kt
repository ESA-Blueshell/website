package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.LocalDate

/** Bank details an applicant set up before their membership started, sealed until it does. */
@Entity
@Table(name = "pending_mandates")
class PendingMandate(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
    @Column(name = "key_id", nullable = false, length = 32)
    var keyId: String,
    @Column(name = "iban", nullable = false, length = 255)
    var ibanCiphertext: String,
    @Column(name = "account_holder", nullable = false, length = 512)
    var accountHolderCiphertext: String,
    @Column(name = "iban_last_four", nullable = false, length = 4)
    var ibanLastFour: String,
    @Column(name = "signed_on", nullable = false)
    var signedOn: LocalDate,
) : AutoIdEntity() {
    // Nothing of the account reaches a log line.
    override fun toString(): String = "PendingMandate(****$ibanLastFour)"
}
