package net.blueshell.api.domain.event.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.committee.persistence.Committee
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.model.AuditedAutoIdEntity
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
        NamedAttributeNode(value = "banner", subgraph = "bannerSub"),
        NamedAttributeNode(value = "signUpForm", subgraph = "formSub"),
    ],
    subgraphs = [
        NamedSubgraph(name = "bannerSub", attributeNodes = [NamedAttributeNode("file")]),
        NamedSubgraph(name = "formSub", attributeNodes = [NamedAttributeNode("_questions")]),
    ]
)
@SQLDelete(sql = "UPDATE events SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Event(
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "committee_id", nullable = false)
    var committee: Committee,

    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "description", length = 4095)
    var description: String? = null,

    @Column(name = "location")
    var location: String? = null,

    @Column(name = "start_time", nullable = false)
    var startTime: Instant,

    @Column(name = "end_time", nullable = false)
    var endTime: Instant,

    @Column(name = "price_member")
    var memberPrice: Double? = null,

    @Column(name = "price_public")
    var publicPrice: Double? = null,

    @Column(name = "google_id")
    var googleId: String? = null,

    @Column(name = "approved", nullable = false)
    var approved: Boolean = false,

    @Column(name = "members_only", nullable = false)
    var membersOnly: Boolean = false,

    @Column(name = "sign_up", nullable = false)
    var signUp: Boolean = false,
) : AuditedAutoIdEntity() {
    val committeeId: Long
        get() = committee.id ?: 0

    @OneToOne(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    var banner: EventBanner? = null
        internal set

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "event", fetch = FetchType.LAZY)
    private val _feedbacks: MutableSet<EventFeedback> = linkedSetOf()
    val feedbacks: Set<EventFeedback>
        get() = _feedbacks

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _pictures: MutableSet<EventPicture> = linkedSetOf()
    val pictures: Set<EventPicture>
        get() = _pictures

    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    @JoinColumn(name = "survey_id")
    var signUpForm: Survey? = null
        internal set

    val signUpFormId: Long?
        get() = signUpForm?.id

    @Column(name = "sign_up_count", nullable = false, updatable = false, insertable = false)
    val signUpCount: Long = 0

    fun replaceBanner(newBanner: EventBanner?) {
        banner = newBanner
        newBanner?.event = this
    }

    fun replaceSignUpForm(newSignUpForm: Survey?) {
        signUpForm = newSignUpForm
    }
}
