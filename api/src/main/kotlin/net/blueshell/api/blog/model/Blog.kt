package net.blueshell.api.blog.model

import jakarta.persistence.*
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(
    name = "blogs",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_blogs_title_deleted_at", columnNames = ["title", "deleted_at"])
    ],
    indexes = [
        Index(name = "idx_blogs_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_blogs_title", columnList = "title"),
        Index(name = "idx_blogs_published_at", columnList = "published_at"),
        Index(name = "idx_blogs_created_at", columnList = "created_at")
    ]
)
@SQLDelete(sql = "UPDATE blogs SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Blog : AuditedAutoIdEntity() {
    @Column(name = "title", nullable = false)
    lateinit var title: String

    @Lob
    @Column(name = "html", nullable = false)
    lateinit var html: String

    @Column(name = "published_at", nullable = false)
    lateinit var publishedAt: Instant
}
