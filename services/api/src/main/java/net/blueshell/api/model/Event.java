package net.blueshell.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import net.blueshell.api.base.BaseModel;
import net.blueshell.api.model.converter.FormQuestionListConverter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "events")
@Data
@SQLDelete(sql = "UPDATE events SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Event implements BaseModel<Long> {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "creator_id")
    @JsonIgnore
    private User creator;

    @OneToOne
    @JoinColumn(name = "last_editor_id")
    @JsonIgnore
    private User lastEditor;

    @OneToOne
    @JoinColumn(name = "committee_id", insertable = false, updatable = false)
    @JsonIgnore
    private Committee committee;

    @Column(name = "committee_id")
    private Long committeeId;

    @JoinColumn(name = "title")
    private String title;

    @JoinColumn(name = "description")
    private String description;

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

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "event")
    @JsonIgnore
    private Set<EventFeedback> feedbacks;

    @OneToMany(mappedBy = "event")
    @JsonIgnore
    private Set<EventPicture> eventPictures;

    @Column(name = "google_id")
    private String googleId;

    @Getter
    @Column(name = "visible")
    private boolean visible;

    @Getter
    @Column(name = "members_only")
    private boolean membersOnly;

    @Column(name = "sign_up")
    private boolean signUp;

    @Column(name = "sign_up_form", columnDefinition = "JSON")
    @Convert(converter = FormQuestionListConverter.class)
    private List<FormQuestion> signUpForm;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;

    /**
     * Get the next month
     *
     * @param month the month is formatted as "yyyy-MM"
     * @return the next month in the format "yyyy-MM"
     */
    private static String nextMonth(String month) {
        final String[] splitMonth = month.split("-");
        if (splitMonth[1].equals("12"))
            return (Integer.parseInt(splitMonth[0]) + 1) + "-01";
        return splitMonth[0] + "-" + (Integer.parseInt(splitMonth[1]) + 1);
    }

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
        return id == event.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
