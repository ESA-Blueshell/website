package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.sql.Timestamp;

@Entity
@Table(
        name = "blogs",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_blogs_title_deleted_at",
                        columnNames = {"title", "deleted_at"}
                )
        },
        indexes = {
                @Index(name = "idx_blogs_title", columnList = "title"),
                @Index(name = "idx_blogs_published_at", columnList = "published_at"),
                @Index(name = "idx_blogs_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE blogs SET deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
public class Blog implements BaseModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title")
    private String title;

    @Column(name = "html")
    private String html;

    @Column(name = "published_at")
    private Timestamp publishedAt;

    @Column(name = "created_at")
    private Timestamp createdAt;

    @Column(name = "deleted_at", nullable = false, insertable=false, updatable = false)
    private Timestamp deletedAt = Timestamp.valueOf("9999-12-31 23:59:59");
}
