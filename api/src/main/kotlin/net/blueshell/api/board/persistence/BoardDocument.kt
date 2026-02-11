package net.blueshell.api.board.persistence

import jakarta.persistence.*
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AuditedSoftDeleteEntity
import net.blueshell.api.shared.model.Identifiable
import net.blueshell.api.shared.model.asRef
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
    @MapsId("boardId")
    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    lateinit var board: Board
        internal set

    val boardId: Long
        get() = id.boardId ?: 0

    @MapsId("fileId")
    @JoinColumn(name = "file_id", nullable = false)
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    lateinit var file: File
        internal set

    val fileId: Long
        get() = id.fileId ?: 0

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
