package net.blueshell.api.model.board

import jakarta.persistence.*
import net.blueshell.api.model.File
import net.blueshell.api.model.base.AuditedSoftDeleteEntity
import net.blueshell.api.model.base.Identifiable
import net.blueshell.api.model.base.asRef
import org.hibernate.Hibernate
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "board_documents",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_board_documents_board_name_deleted_at",
            columnNames = ["board_id", "name", "deleted_at"]
        ),
        UniqueConstraint(
            name = "uk_board_documents_file_deleted_at",
            columnNames = ["file_id", "deleted_at"]
        )
    ],
    indexes = [
        Index(name = "idx_board_documents_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_board_documents_board_id", columnList = "board_id"),
        Index(name = "idx_board_documents_file_id", columnList = "file_id")
    ]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(
    sql = """
      UPDATE board_documents
      SET deleted_at = NOW(), version = version + 1
      WHERE board_id = ? AND file_id = ? AND version = ?
    """
)
class BoardDocument(
    @EmbeddedId
    override var id: Id = Id()
) : AuditedSoftDeleteEntity(), Identifiable<BoardDocument.Id> {
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

    @field:Column(name = "board_id", nullable = false, updatable = false, insertable = false)
    var boardId: Long = 0
        get() = requireNotNull(id.boardId) { "Board ID is required" }
        set(value) {
            field = value
            id.boardId = value
            if (_board?.id != value) {
                _board = Board::class.asRef(value)
            }
        }

    @field:MapsId("fileId")
    @field:JoinColumn(name = "file_id", nullable = false)
    @field:OneToOne(fetch = FetchType.LAZY, optional = false)
    private var _file: File? = null
    var file: File
        get() = requireNotNull(_file) { "File is required" }
        set(value) {
            _file = value
            value.id?.let { fileId = it }
        }

    @field:Column(name = "file_id", nullable = false, updatable = false, insertable = false)
    var fileId: Long = 0
        get() = requireNotNull(id.fileId) { "File ID is required" }
        set(value) {
            field = value
            id.fileId = value
            if (_file?.id != value) {
                _file = File::class.asRef(value)
            }
        }

    @Column(name = "name", nullable = false)
    lateinit var name: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (Hibernate.getClass(this) != Hibernate.getClass(other)) return false
        other as BoardDocument
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    @Embeddable
    data class Id(
        var boardId: Long? = null,
        var fileId: Long? = null
    ) : java.io.Serializable
}
