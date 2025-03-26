package net.blueshell.telemetry.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import net.blueshell.api.business.user.User;

import java.sql.Timestamp;
import java.util.Objects;

@Entity
@Table(name = "news")
@Data
public class Telemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @OneToOne
    @JoinColumn(name = "creator_id")
    private User author;

    @OneToOne
    @JoinColumn(name = "last_editor_id")
    private User lastEditor;

    @Column(name = "news_type")
    private String newsType;

    private String title;

    private String content;

    @Column(name = "posted_at")
    private Timestamp postedAt;

    public Telemetry() {

    }

    public Telemetry(User author, String newsType, String title, String content, Timestamp postedAt) {
        this.author = author;
        this.newsType = newsType;
        this.title = title;
        this.content = content;
        this.postedAt = postedAt;
    }

    @JsonProperty("author")
    public long getAuthorId() { return getAuthor() == null ? 0 : getAuthor().getId(); }

    @JsonProperty("lastEditor")
    public long getLastEditorId() { return getLastEditor() == null ? 0 : getLastEditor().getId(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        News news = (News) o;
        return id == news.id;
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

}
