package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.LocalDate

/** What kind of member someone is within one membership, from [since] until the next type (api ADR-040). */
@Entity
@Table(
    name = "membership_types",
    uniqueConstraints = [UniqueConstraint(name = "uk_membership_types_day", columnNames = ["membership_id", "since"])],
)
class MembershipType(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    var membership: Membership,
    @Column(name = "type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    var type: MemberType,
    @Column(name = "since", nullable = false)
    var since: LocalDate,
) : AutoIdEntity()
