package net.blueshell.blogservice.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.db.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;
import java.util.UUID;

@Entity
@Table(name = "blogs")
@SQLDelete(sql = "UPDATE blogs SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Data
public class Blog implements BaseModel<UUID> {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String title;

    private String text;

    private String html;

    private String markdown;

    @Column(name = "published_at")
    private Timestamp publishedAt;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "deleted_at")
    private Timestamp deletedAt;
}
