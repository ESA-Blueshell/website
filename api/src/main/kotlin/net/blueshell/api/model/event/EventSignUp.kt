package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.JpaListener
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.model.User
import net.blueshell.api.model.survey.Answer
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
    name = "EventSignUp.withGuestAndAnswers",
    attributeNodes = [
        NamedAttributeNode("_guest"),
        NamedAttributeNode(value = "_answers", subgraph = "answersSub")
    ],
    subgraphs = [
        NamedSubgraph(name = "answersSub", attributeNodes = [NamedAttributeNode("_question")])
    ]
)
@SQLDelete(sql = "UPDATE event_signups SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
open class EventSignUp : AuditedAutoIdEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "event_id", insertable = false, updatable = false, nullable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
            eventId = value.id ?: eventId
        }

    @Column(name = "event_id", nullable = false)
    var eventId: Long = 0

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id", insertable = false, updatable = false)
    private var _user: User? = null
    var user: User?
        get() = _user
        set(value) {
            _user = value
            userId = value?.id ?: userId
        }

    @Column(name = "user_id")
    var userId: Long? = null

    @field:ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH], fetch = FetchType.LAZY)
    @field:JoinColumn(name = "guest_id")
    private var _guest: Guest? = null
    var guest: Guest?
        get() = _guest
        set(value) {
            _guest = value
        }

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
