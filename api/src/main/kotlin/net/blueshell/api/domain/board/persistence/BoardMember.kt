package net.blueshell.api.domain.board.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.domain.user.persistence.User
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.io.Serializable

@Entity
@Table(
    name = "board_members",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_board_members_board_user_deleted_at",
            columnNames = ["board_id", "user_id", "deleted_at"]
        ),
        UniqueConstraint(
            name = "uk_board_members_picture_deleted_at",
            columnNames = ["picture_id", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_board_members_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_board_members_board_id", columnList = "board_id"),
        Index(name = "idx_board_members_user_id", columnList = "user_id")
    ]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(
    sql = """
      UPDATE board_members
      SET deleted_at = NOW(), version = version + 1
      WHERE board_id = ? AND user_id = ? AND version = ?
    """
)
class BoardMember(
    @EmbeddedId
    override var id: Id = Id()
) : AuditedSoftDeleteEntity(), Identifiable<BoardMember.Id> {
    @MapsId("boardId")
    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    lateinit var board: Board
        internal set

    val boardId: Long
        get() = id.boardId ?: 0

    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    lateinit var user: User
        internal set

    val userId: Long
        get() = id.userId ?: 0

    @JoinColumn(name = "picture_id")
    @OneToOne(fetch = FetchType.LAZY)
    var picture: File? = null
        internal set

    val pictureId: Long?
        get() = picture?.id

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as BoardMember
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var boardId: Long? = null,
        var userId: Long? = null
    ) : Serializable
}
