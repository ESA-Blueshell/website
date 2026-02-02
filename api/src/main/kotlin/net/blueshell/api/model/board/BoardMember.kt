package net.blueshell.api.model.board

import jakarta.persistence.*
import lombok.Data
import lombok.EqualsAndHashCode
import lombok.NoArgsConstructor
import lombok.ToString
import net.blueshell.api.base.BaseModel
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
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@NoArgsConstructor
@ToString(onlyExplicitlyIncluded = true, callSuper = true)
class BoardMember : BaseModel() {
    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne
    private val board: Board? = null

    @Column(name = "board_id", updatable = false, insertable = false)
    @ToString.Include
    private var boardId: Long? = null

    @JoinColumn(name = "user_id", nullable = false)
    @ManyToOne
    private val user: User? = null

    @Column(name = "user_id", updatable = false, insertable = false)
    @ToString.Include
    private var userId: Long? = null

    @JoinColumn(name = "picture_id")
    @OneToOne
    private val picture: File? = null
}
