package net.blueshell.api.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import net.blueshell.api.base.BaseModel;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

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
                @Index(name = "idx_blogs_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_blogs_title", columnList = "title"),
                @Index(name = "idx_blogs_published_at", columnList = "published_at"),
                @Index(name = "idx_blogs_created_at", columnList = "created_at")
        }
)
@SQLDelete(sql = "UPDATE blogs SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
public class Blog extends BaseModel {
    @Column(name = "title", nullable = false)
    @ToString.Include
    private String title;

    @Lob
    @Column(name = "html", nullable = false)
    @ToString.Include
    private String html;

    @Column(name = "published_at", nullable = false)
    @ToString.Include
    private Instant publishedAt;
}
