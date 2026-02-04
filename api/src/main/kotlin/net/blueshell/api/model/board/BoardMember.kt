package net.blueshell.api.model.board

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.model.File
import net.blueshell.api.model.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "board_members",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_board_members_board_user_deleted_at",
        columnNames = ["board_id", "user_id", "deleted_at"]
    ), UniqueConstraint(name = "uk_board_members_picture_deleted_at", columnNames = ["picture_id", "deleted_at"])],
    indexes = [Index(
        name = "idx_board_members_deleted_at",
        columnList = "deleted_at"
    ), Index(name = "idx_board_members_board_id", columnList = "board_id"), Index(
        name = "idx_board_members_user_id",
        columnList = "user_id"
    )]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE board_members SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
class BoardMember : AuditedAutoIdEntity() {
    @field:JoinColumn(name = "board_id", nullable = false, insertable = false, updatable = false)
    @field:ManyToOne(fetch = FetchType.LAZY)
    private var _board: Board? = null
    var board: Board
        get() = requireNotNull(_board) { "Board is required" }
        set(value) {
            _board = value
            boardId = value.id ?: boardId
        }

    @Column(name = "board_id", nullable = false)
    var boardId: Long = 0

    @field:JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    @field:ManyToOne(fetch = FetchType.LAZY)
    private var _user: User? = null
    var user: User
        get() = requireNotNull(_user) { "User is required" }
        set(value) {
            _user = value
            userId = value.id ?: userId
        }

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0

    @field:JoinColumn(name = "picture_id")
    @field:OneToOne(fetch = FetchType.LAZY)
    private var _picture: File? = null
    var picture: File?
        get() = _picture
        set(value) {
            _picture = value
        }
}
