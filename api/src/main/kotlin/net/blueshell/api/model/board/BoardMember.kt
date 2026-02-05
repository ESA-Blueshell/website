package net.blueshell.api.model.board

import jakarta.persistence.*
import net.blueshell.api.model.base.AuditedSoftDeleteEntity
import net.blueshell.api.model.base.Identifiable
import net.blueshell.api.model.File
import net.blueshell.api.model.User
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
open class BoardMember(
    @EmbeddedId
    override var id: BoardMemberId = BoardMemberId()
) : AuditedSoftDeleteEntity(), Identifiable<BoardMemberId> {

    @get:Transient
    @set:Transient
    var boardId: Long
        get() = requireNotNull(id.boardId) { "boardId is required" }
        set(value) {
            id.boardId = value
        }

    @get:Transient
    @set:Transient
    var userId: Long
        get() = requireNotNull(id.userId) { "userId is required" }
        set(value) {
            id.userId = value
        }

    @field:MapsId("boardId")
    @field:JoinColumn(name = "board_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    private var _board: Board? = null
    var board: Board
        get() = requireNotNull(_board) { "Board is required" }
        set(value) {
            _board = value
            value.id?.let { boardId = it }
        }

    @field:MapsId("userId")
    @field:JoinColumn(name = "user_id", nullable = false)
    @field:ManyToOne(fetch = FetchType.LAZY, optional = false)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            value.id?.let { userId = it }
        }

    @field:JoinColumn(name = "picture_id")
    @field:OneToOne(fetch = FetchType.LAZY)
    private var _picture: File? = null
    var picture: File?
        get() = _picture
        set(value) {
            _picture = value
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as BoardMember
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Embeddable
data class BoardMemberId(
    var boardId: Long? = null,
    var userId: Long? = null
) : java.io.Serializable
