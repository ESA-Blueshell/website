package net.blueshell.api.domain.board.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

/**
 * One seat on one board.
 *
 * The seat is identified by its own id rather than by the pair of board and member, because
 * most of the people who have sat on a board never had an account here: eight of the nine
 * boards predate this system. [user] is therefore nullable, and [displayName] carries who
 * held the seat when nobody can be linked to it. A seat that is linked is what membership
 * questions read; a seat that is not is still the board that sat.
 *
 * [image] names an asset the frontend ships, the way a team's image does. [picture] stays for
 * a portrait uploaded through the file service.
 */
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
      WHERE id = ? AND version = ?
    """
)
class BoardMember(

    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    val board: Board,

    @JoinColumn(name = "user_id", nullable = true)
    @ManyToOne(fetch = FetchType.LAZY)
    var user: User? = null,

    @Column(nullable = false)
    var role: String,

    @Column(nullable = false)
    var startDate: LocalDate,

    @Column()
    var endDate: LocalDate? = null,

    /** Who held the seat, for a seat no account can be attached to. */
    @Column(name = "display_name", length = 128)
    var displayName: String? = null,

    /** The personal note the board page has always shown beside a member. */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    @Column(name = "image", length = 255)
    var image: String? = null,

) : AuditedAutoIdEntity() {
    val boardId: Long
        get() = board.id ?: 0

    val userId: Long?
        get() = user?.id

    @JoinColumn(name = "picture_id")
    @OneToOne(fetch = FetchType.LAZY)
    var picture: File? = null
        internal set

    val pictureId: Long?
        get() = picture?.id

    /** The name to show: the member's own when one is linked, the recorded one otherwise. */
    val name: String?
        get() = user?.fullName ?: displayName
}
