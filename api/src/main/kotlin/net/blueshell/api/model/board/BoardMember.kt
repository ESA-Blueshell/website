package net.blueshell.api.model.board

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.model.File
import net.blueshell.api.model.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import kotlin.properties.Delegates

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
class BoardMember : BaseModel() {
    @JoinColumn(name = "board_id", nullable = false, insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    lateinit var board: Board
        get() = field
        set(value) {
            field = value
            boardId = value.id ?: boardId
        }

    @Column(name = "board_id", nullable = false)
    var boardId: Long by kotlin.properties.Delegates.notNull()

    @JoinColumn(name = "user_id", nullable = false, insertable = false, updatable = false)
    @ManyToOne(fetch = FetchType.LAZY)
    lateinit var user: User
        get() = field
        set(value) {
            field = value
            userId = value.id ?: userId
        }

    @Column(name = "user_id", nullable = false)
    var userId: Long by kotlin.properties.Delegates.notNull()

    @JoinColumn(name = "picture_id")
    @OneToOne(fetch = FetchType.LAZY)
    val picture: File? = null
}
