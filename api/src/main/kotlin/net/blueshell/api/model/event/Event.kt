package net.blueshell.api.model.event

import jakarta.persistence.*
import lombok.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.base.JpaListener
import net.blueshell.api.model.committee.Committee
import net.blueshell.api.model.survey.Survey
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

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
        subgraphs = [NamedSubgraph(name = "bannerSub", attributeNodes = [NamedAttributeNode("file")]), NamedSubgraph(
            name = "formSub",
            attributeNodes = [NamedAttributeNode("questions")]
        )]
    )
)
@SQLDelete(sql = "UPDATE events SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener::class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class Event : BaseModel() {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", insertable = false, updatable = false)
    private val committee: Committee? = null

    @Column(name = "committee_id")
    @ToString.Include
    private var committeeId: Long? = null

    @Column(name = "title", nullable = false)
    @ToString.Include
    private var title: String? = null

    @Column(name = "description", length = 4095)
    @ToString.Include
    private var description: String? = null

    @Column(name = "location")
    @ToString.Include
    private var location: String? = null

    @Column(name = "start_time", nullable = false)
    @ToString.Include
    private var startTime: Instant? = null

    @Column(name = "end_time", nullable = false)
    @ToString.Include
    private var endTime: Instant? = null

    @OneToOne(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    private val banner: EventBanner? = null

    @Column(name = "price_member")
    @ToString.Include
    private var memberPrice: Double? = null

    @Column(name = "price_public")
    @ToString.Include
    private var publicPrice: Double? = null

    @OneToMany(cascade = [CascadeType.ALL], mappedBy = "event", fetch = FetchType.LAZY)
    private val feedbacks: MutableSet<EventFeedback?>? = null

    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val pictures: MutableSet<EventPicture?>? = null

    @Column(name = "google_id")
    @ToString.Include
    private var googleId: String? = null

    @Getter
    @Column(name = "approved", nullable = false)
    @ToString.Include
    private var approved = false

    @Getter
    @Column(name = "members_only", nullable = false)
    @ToString.Include
    private var membersOnly = false

    @Getter
    @Column(name = "sign_up", nullable = false)
    @ToString.Include
    private var signUp = false

    @JoinColumn(name = "survey_id")
    @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], orphanRemoval = true)
    private val signUpForm: Survey? = null

    @Column(name = "survey_id", updatable = false, insertable = false)
    @ToString.Include
    private var signUpFormId: Long? = null

    @Column(name = "sign_up_count", nullable = false, updatable = false, insertable = false)
    @ToString.Include
    private var signUpCount: Long? = null
}
