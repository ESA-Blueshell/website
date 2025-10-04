package net.blueshell.api.model.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.base.JpaListener;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.model.survey.Survey;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "events")
@Data
@SQLDelete(sql = "UPDATE events SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at >= NOW()")
@EntityListeners(JpaListener.class)
public class Event implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", insertable = false, updatable = false)
    @JsonIgnore
    private User creator;

    @Column(name = "creator_Id")
    private Long creatorId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_editor_id", insertable = false, updatable = false)
    @JsonIgnore
    private User lastEditor;

    @Column(name = "last_editor_id")
    private Long lastEditorId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "committee_id", insertable = false, updatable = false)
    @JsonIgnore
    private Committee committee;

    @Column(name = "committee_id")
    private Long committeeId;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "location")
    private String location;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @OneToOne
    @JoinColumn(name = "banner_id")
    @JsonIgnore
    private File banner;

    @Column(name = "price_member")
    private Double memberPrice;

    @Column(name = "price_public")
    private Double publicPrice;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "event", fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<EventFeedback> feedbacks;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private Set<EventPicture> pictures;

    @Column(name = "google_id")
    private String googleId;

    @Getter
    @Column(name = "visible")
    private boolean visible;

    @Getter
    @Column(name = "members_only")
    private boolean membersOnly;

    @Getter
    @Column(name = "sign_up")
    private boolean signUp;

    @JoinColumn(name = "survey_id")
    @OneToOne(fetch = FetchType.LAZY)
    private Survey signUpForm;

    @Column(name = "survey_id", updatable = false, insertable = false)
    private Long signUpFormId;

    @JsonProperty("creator")
    public long getCreatorId() {
        return getCreator() == null ? 0 : getCreator().getId();
    }

    @JsonProperty("lastEditor")
    public long getLastEditorId() {
        return getLastEditor() == null ? 0 : getLastEditor().getId();
    }

    @JsonProperty("committee")
    public long getCommitteeId() {
        return getCommittee() == null ? 0 : getCommittee().getId();
    }

    @JsonProperty("banner")
    public String getBannerId() {
        return this.getBanner() == null ? null : this.getBanner().getUrl();
    }

    @JsonProperty("feedbacks")
    public Set<Long> getFeedbackIds() {
        Set<Long> set = new HashSet<>();
        if (getFeedbacks() == null) {
            return set;
        }
        for (EventFeedback ef : getFeedbacks()) {
            set.add(ef.getId());
        }
        return set;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
