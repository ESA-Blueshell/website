package net.blueshell.api.event.persistence

import jakarta.persistence.*
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "event_signups",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_event_signups_event_user_deleted_at",
            columnNames = ["event_id", "user_id", "deleted_at"]
        ),
        UniqueConstraint(
            name = "uk_event_signups_event_guest_deleted_at",
            columnNames = ["event_id", "guest_id", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_event_signups_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_event_signups_event_id", columnList = "event_id"),
        Index(name = "idx_event_signups_user_id", columnList = "user_id"),
        Index(name = "idx_event_signups_guest_id", columnList = "guest_id")
    ]
)
@NamedEntityGraph(
    name = "EventSignUp.withGuestUserAndAnswers",
    attributeNodes = [
        NamedAttributeNode("guest"),
        NamedAttributeNode("user"),
        NamedAttributeNode(value = "_answers", subgraph = "answersSub")
    ],
    subgraphs = [
        NamedSubgraph(name = "answersSub", attributeNodes = [NamedAttributeNode("question")])
    ]
)
@SQLDelete(sql = "UPDATE event_signups SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class EventSignUp(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    var event: Event,

    @Column(name = "user_id")
    var userId: Long? = null,

    @ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH], fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    var guest: Guest? = null,
) : AuditedAutoIdEntity() {
    val eventId: Long
        get() = event.id ?: 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    var user: User? = null

    // A link with no state of its own: Answer carries the soft-delete, so a deleted answer is
    // already hidden here. The link table's own deleted_at is historical, seeded from the sign-up.
    @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL])
    @JoinTable(
        name = "event_sign_up_answers",
        joinColumns = [JoinColumn(name = "event_sign_up_id")],
        inverseJoinColumns = [JoinColumn(name = "answer_id")]
    )
    private val _answers: MutableSet<Answer> = linkedSetOf()
    val answers: Set<Answer>
        get() = _answers
}
