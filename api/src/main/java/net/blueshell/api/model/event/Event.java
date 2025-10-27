package net.blueshell.api.model.event;

import jakarta.persistence.*;
import lombok.*;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.survey.Survey;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.Set;

@Entity
@Table(
        name = "events",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_events_google_id_deleted_at", columnNames = {"google_id", "deleted_at"})
        },
        indexes = {
                @Index(name = "idx_events_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_events_committee_id", columnList = "committee_id"),
                @Index(name = "idx_events_start_time", columnList = "start_time"),
                @Index(name = "idx_events_end_time", columnList = "end_time"),
                @Index(name = "idx_events_title", columnList = "title"),
                @Index(name = "idx_events_approved", columnList = "approved"),
                @Index(name = "idx_events_members_only", columnList = "members_only"),
                @Index(name = "idx_events_sign_up", columnList = "sign_up")
        }
)
@NamedEntityGraphs({
        @NamedEntityGraph(
                name = "Event.withBannerFileAndFormQuestions",
                attributeNodes = {
                        @NamedAttributeNode(value = "banner", subgraph = "bannerSub"),
                        @NamedAttributeNode(value = "signUpForm", subgraph = "formSub")
                },
                subgraphs = {
                        @NamedSubgraph(
                                name = "bannerSub",
                                attributeNodes = {@NamedAttributeNode("file")}
                        ),
                        @NamedSubgraph(
                                name = "formSub",
                                attributeNodes = {@NamedAttributeNode("questions")}
                        )
                }
        )
})
@SQLDelete(sql = "UPDATE events SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@EntityListeners(JpaListener.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
public class Event extends BaseModel {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", insertable = false, updatable = false)
    private Committee committee;

    @Column(name = "committee_id")
    private Long committeeId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", length = 4095)
    private String description;

    @Column(name = "location")
    private String location;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @OneToOne(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private EventBanner banner;

    @Column(name = "price_member")
    private Double memberPrice;

    @Column(name = "price_public")
    private Double publicPrice;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "event", fetch = FetchType.LAZY)
    private Set<EventFeedback> feedbacks;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Set<EventPicture> pictures;

    @Column(name = "google_id")
    private String googleId;

    @Getter
    @Column(name = "approved", nullable = false)
    private boolean approved;

    @Getter
    @Column(name = "members_only", nullable = false)
    private boolean membersOnly;

    @Getter
    @Column(name = "sign_up", nullable = false)
    private boolean signUp;

    @JoinColumn(name = "survey_id")
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Survey signUpForm;

    @Column(name = "survey_id", updatable = false, insertable = false)
    private Long signUpFormId;

    @Column(name = "sign_up_count", nullable = false, updatable = false, insertable = false)
    private Long signUpCount;
}
