package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.common.jpa.JpaListener
import net.blueshell.api.model.base.AuditedAutoIdEntity
import net.blueshell.api.model.base.asRef
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.survey.Survey
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(
    name = "events",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_events_google_id_deleted_at", columnNames = ["google_id", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_events_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_events_committee_id", columnList = "committee_id"),
        Index(name = "idx_events_start_time", columnList = "start_time"),
        Index(name = "idx_events_end_time", columnList = "end_time"),
        Index(name = "idx_events_title", columnList = "title"),
        Index(name = "idx_events_approved", columnList = "approved"),
        Index(name = "idx_events_members_only", columnList = "members_only"),
        Index(name = "idx_events_sign_up", columnList = "sign_up")
    ]
)
@NamedEntityGraph(
    name = "Event.withBannerFileAndFormQuestions",
    attributeNodes = [
        NamedAttributeNode(value = "_banner", subgraph = "bannerSub"),
        NamedAttributeNode(value = "_signUpForm", subgraph = "formSub"),
    ],
    subgraphs = [
        NamedSubgraph(name = "bannerSub", attributeNodes = [NamedAttributeNode("_file")]),
        NamedSubgraph(name = "formSub", attributeNodes = [NamedAttributeNode("_questions")]),
    ]
)
@SQLDelete(sql = "UPDATE events SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class Event : AuditedAutoIdEntity() {
    @field:ManyToOne(fetch = FetchType.LAZY)
    @field:JoinColumn(name = "committee_id")
    private var _committee: Committee? = null
    var committee: Committee
        get() = requireNotNull(_committee) { "Committee is required" }
        set(value) {
            _committee = value
            committeeId = value.id ?: committeeId
        }

    @field:Column(name = "committee_id", nullable = false, updatable = false, insertable = false)
    var committeeId: Long = 0
        get() = requireNotNull(_committee?.id) { "Committee ID is required" }
        set(value) {
            field = value
            if (_committee?.id != value) {
                _committee = Committee::class.asRef(value)
            }
        }

    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Column(name = "description", length = 4095)
    var description: String? = null

    @Column(name = "location")
    var location: String? = null

    @Column(name = "start_time", nullable = false)
    lateinit var startTime: Instant

    @Column(name = "end_time", nullable = false)
    lateinit var endTime: Instant

    @field:OneToOne(mappedBy = "_event", cascade = [CascadeType.ALL], orphanRemoval = true)
    private var _banner: EventBanner? = null
    var banner: EventBanner?
        get() = _banner
        set(value) {
            _banner = value
        }

    @Column(name = "price_member")
    var memberPrice: Double? = null

    @Column(name = "price_public")
    var publicPrice: Double? = null

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "_event", fetch = FetchType.LAZY)
    private val _feedbacks: MutableSet<EventFeedback> = linkedSetOf()
    val feedbacks: Set<EventFeedback>
        get() = _feedbacks

    @OneToMany(mappedBy = "_event", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _pictures: MutableSet<EventPicture> = linkedSetOf()
    val pictures: Set<EventPicture>
        get() = _pictures

    @Column(name = "google_id")
    var googleId: String? = null

    @Column(name = "approved", nullable = false)
    var approved = false

    @Column(name = "members_only", nullable = false)
    var membersOnly = false

    @Column(name = "sign_up", nullable = false)
    var signUp = false

    @field:JoinColumn(name = "survey_id")
    @field:OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    private var _signUpForm: Survey? = null
    var signUpForm: Survey?
        get() = _signUpForm
        set(value) {
            _signUpForm = value
            signUpFormId = value?.id
        }

    @field:Column(name = "survey_id", updatable = false, insertable = false)
    var signUpFormId: Long? = null
        get() = _signUpForm?.id
        set(value) {
            field = value
            if (value == null) {
                _signUpForm = null
            } else if (_signUpForm?.id != value) {
                _signUpForm = Survey::class.asRef(value)
            }
        }

    @Column(name = "sign_up_count", nullable = false, updatable = false, insertable = false)
    var signUpCount: Long = 0
}
