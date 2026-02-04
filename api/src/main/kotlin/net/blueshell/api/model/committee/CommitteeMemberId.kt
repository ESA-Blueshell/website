package net.blueshell.api.model.committee

import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class CommitteeMemberId(
    @Column(name = "committee_id")
    var committeeId: Long = 0,

    @Column(name = "user_id")
    var userId: Long = 0
) : java.io.Serializable