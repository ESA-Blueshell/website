package net.blueshell.api.board.persistence

import jakarta.persistence.*
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.asRef
import net.blueshell.api.user.persistence.User
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

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
    @field:MapsId("boardId")
    @field:JoinColumn(name = "board_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    private var _board: Board? = null
    var board: Board
        get() = requireNotNull(_board) { "Board is required" }
        set(value) {
            _board = value
            boardId = _board?.id ?: boardId
        }


    @field:Column(name = "board_id", nullable = false, updatable = false, insertable = false)
    var boardId: Long = 0
        get() = id.boardId ?: field
        set(value) {
            field = value
            id.boardId = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _board?.id) {
                _board = Board::class.asRef(value)
            }
        }


    @field:JoinColumn(name = "picture_id")
    @field:OneToOne(fetch = FetchType.LAZY)
    private var _picture: File? = null

    @field:MapsId("userId")
    @field:JoinColumn(name = "user_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            userId = _user?.id ?: userId
        }

    @field:Column(name = "user_id", nullable = false, updatable = false, insertable = false)
    var userId: Long = 0
        get() = id.userId ?: field
        set(value) {
            field = value
            id.userId = value
            // Only override the reference, if the ref exists and is different from current
            if (value != 0L && value != _user?.id) {
                _user = User::class.asRef(value)
            }
        }

    var picture: File?
        get() = _picture
        set(value) {
            _picture = value
            pictureId = _picture?.id ?: pictureId
        }

    @field:Column(name = "picture_id", updatable = false, insertable = false)
    var pictureId: Long? = null
        get() = _picture?.id ?: field
        set(value) {
            field = value
            if (value == null) {
                _picture = null
            } else if (_picture?.id != value) {
                _picture = File::class.asRef(value)
            }
        }

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
    ) : java.io.Serializable
}
