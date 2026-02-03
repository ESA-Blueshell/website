package net.blueshell.api.model.event

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.survey.Survey
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant
import kotlin.properties.Delegates

@Entity
@Table(
    name = "events",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_events_google_id_deleted_at",
        columnNames = ["google_id", "deleted_at"]
    )],
    indexes = [Index(name = "idx_events_deleted_at", columnList = "deleted_at"), Index(
        name = "idx_events_committee_id",
        columnList = "committee_id"
    ), Index(name = "idx_events_start_time", columnList = "start_time"), Index(
        name = "idx_events_end_time",
        columnList = "end_time"
    ), Index(name = "idx_events_title", columnList = "title"), Index(
        name = "idx_events_approved",
        columnList = "approved"
    ), Index(name = "idx_events_members_only", columnList = "members_only"), Index(
        name = "idx_events_sign_up",
        columnList = "sign_up"
    )]
)
@NamedEntityGraphs(
    NamedEntityGraph(
        name = "Event.withBannerFileAndFormQuestions",
        attributeNodes = [NamedAttributeNode(
            value = "banner",
            subgraph = "bannerSub"
        ), NamedAttributeNode(value = "signUpForm", subgraph = "formSub")],
        subgraphs = [
            NamedSubgraph(
                name = "bannerSub", attributeNodes = [
                    NamedAttributeNode("file")]
            ), NamedSubgraph(
                name = "formSub",
                attributeNodes = [NamedAttributeNode("questions")]
            )]
    )
)
@SQLDelete(sql = "UPDATE events SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
class Event : BaseModel() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", insertable = false, updatable = false)
    var committee: Committee? = null

    @Column(name = "committee_id")
    var committeeId: Long? = null

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

    @OneToOne(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var banner: EventBanner? = null

    @Column(name = "price_member")
    var memberPrice: Double? = null

    @Column(name = "price_public")
    var publicPrice: Double? = null

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "event", fetch = FetchType.LAZY)
    private val _feedbacks: MutableSet<EventFeedback> = linkedSetOf()
    val feedbacks: Set<EventFeedback>
        get() = _feedbacks

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
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

    @JoinColumn(name = "survey_id")
    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    var signUpForm: Survey? = null

    @Column(name = "survey_id", updatable = false, insertable = false)
    var signUpFormId: Long? = null

    @Column(name = "sign_up_count", nullable = false, updatable = false, insertable = false)
    var signUpCount: Long = 0
}
