package net.blueshell.api.event.model

import jakarta.persistence.*
import net.blueshell.api.shared.jpa.JpaListener
import net.blueshell.api.user.model.User
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.survey.model.Answer
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
class EventSignUp : AuditedAutoIdEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    @field:JoinColumn(name = "event_id", nullable = false)
    private var _event: Event? = null
    var event: Event
        get() = requireNotNull(_event) { "Event is required" }
        set(value) {
            _event = value
            eventId = value.id ?: eventId
        }

    @field:Column(name = "event_id", nullable = false, updatable = false, insertable = false)
    var eventId: Long = 0
        get() = _event?.id ?: field
        set(value) {
            field = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _event?.id) {
                _event = Event::class.asRef(value)
            }
        }

    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "user_id")
    private var _user: User? = null
    var user: User?
        get() = _user
        set(value) {
            _user = value
            userId = value?.id
        }

    @field:Column(name = "user_id", updatable = false, insertable = false)
    var userId: Long? = null
        get() = _user?.id
        set(value) {
            field = value
            if (value == null) {
                _user = null
            } else if (_user?.id != value) {
                _user = User::class.asRef(value)
            }
        }

    @field:ManyToOne(cascade = [CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REFRESH], fetch = FetchType.LAZY)
    @field:JoinColumn(name = "guest_id")
    private var _guest: Guest? = null
    var guest: Guest?
        get() = _guest
        set(value) {
            _guest = value
        }

    @field:Column(name = "guest_id", updatable = false, insertable = false)
    var guestId: Long? = null
        get() = _guest?.id
        set(value) {
            field = value
            if (value == null) {
                _guest = null
            } else if (_guest?.id != value) {
                _guest = Guest::class.asRef(value)
            }
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
