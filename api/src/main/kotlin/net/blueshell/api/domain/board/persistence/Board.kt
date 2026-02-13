package net.blueshell.api.domain.board.persistence

import jakarta.persistence.*
import net.blueshell.api.domain.file.persistence.File
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate

@Entity
@Table(
    name = "boards",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_boards_name_start_date_deleted_at",
            columnNames = ["name", "start_date", "deleted_at"]
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
class Board : AuditedAutoIdEntity() {
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

    @Column(name = "candidate", nullable = false)
    lateinit var candidate: String

    @Column(name = "start_date", nullable = false)
    lateinit var startDate: LocalDate

    @Column(name = "end_date")
    var endDate: LocalDate? = null

    @Column(name = "name", nullable = false)
    lateinit var name: String

    fun addMember(member: BoardMember) {
        member.board = this
        _members.add(member)
    }

    fun removeMember(userId: Long) {
        _members.removeIf { it.userId == userId }
    }

    fun addDocument(document: BoardDocument) {
        document.board = this
        _documents.add(document)
    }

    fun removeDocument(fileId: Long) {
        _documents.removeIf { it.fileId == fileId }
    }

    fun replacePicture(newPicture: File?) {
        picture = newPicture
    }
}
