package net.blueshell.api.board.persistence

import jakarta.persistence.*
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

/**
 * One year of the association's life: the group that ran it and what that group was called.
 *
 * [number] is the board's place in the line and its identity — the ninth board is number 9.
 * [name] is the name a board chose for itself and is optional, because a board's name is a
 * thing that may never have been written down. [candidate] duplicates the name and nothing
 * reads it; it is kept by decision and every write fills it, so it can stay `NOT NULL`.
 */
@Entity
@Table(
    name = "boards",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_boards_number_deleted_at",
            columnNames = ["number", "deleted_at"]
        ),
        UniqueConstraint(
            name = "uk_boards_picture_deleted_at",
            columnNames = ["picture_id", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_boards_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_boards_name", columnList = "name"),
        Index(name = "idx_boards_start_date", columnList = "start_date"),
        Index(name = "idx_boards_end_date", columnList = "end_date")
    ]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE boards SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
class Board(
    /** The board's ordinal in the line, unique among the boards that exist. */
    @Column(name = "number", nullable = false)
    var number: Int,

    @Column(name = "candidate", nullable = false)
    var candidate: String,

    @Column(name = "start_date", nullable = false)
    var startDate: LocalDate,

    /** The name the board chose for itself, where one is recorded. */
    @Column(name = "name")
    var name: String? = null,

    @Column(name = "end_date")
    var endDate: LocalDate? = null,

    /** The board's shouted line. */
    @Column(name = "cheer", length = 255)
    var cheer: String? = null,

    /** The board's own colour, blank meaning the association's blue. Mirrors a game's accent. */
    @Column(name = "accent", length = 32)
    var accent: String? = null,

    /** What the year was about, in the board's own words. */
    @Column(name = "description", columnDefinition = "TEXT")
    var description: String? = null,

    /** Asset file name of the board's photograph, the way a team's image is held. */
    @Column(name = "image", length = 255)
    var image: String? = null,
) : AuditedAutoIdEntity() {
    @JoinColumn(name = "picture_id")
    @OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var picture: File? = null
        internal set

    val pictureId: Long?
        get() = picture?.id

    @OneToMany(mappedBy = "board", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _members: MutableSet<BoardMember> = linkedSetOf()
    val members: Set<BoardMember>
        get() = _members

    @OneToMany(mappedBy = "board", fetch = FetchType.LAZY)
    private val _documents: MutableSet<BoardDocument> = linkedSetOf()
    val documents: Set<BoardDocument>
        get() = _documents

    fun addMember(member: BoardMember) {
        _members.add(member)
    }

    fun removeMember(userId: Long) {
        _members.removeIf { it.userId == userId }
    }

    fun addDocument(document: BoardDocument) {
        _documents.add(document)
    }

    fun removeDocument(fileId: Long) {
        _documents.removeIf { it.fileId == fileId }
    }

    fun replacePicture(newPicture: File?) {
        picture = newPicture
    }
}
