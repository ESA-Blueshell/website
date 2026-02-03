package net.blueshell.api.model.board

import jakarta.persistence.*
import net.blueshell.api.base.BaseModel
import net.blueshell.api.model.File
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "board_documents",
    uniqueConstraints = [UniqueConstraint(
        name = "uk_board_documents_board_name_deleted_at",
        columnNames = ["board_id", "name", "deleted_at"]
    ), UniqueConstraint(name = "uk_board_documents_file_deleted_at", columnNames = ["file_id", "deleted_at"])],
    indexes = [Index(
        name = "idx_board_documents_deleted_at",
        columnList = "deleted_at"
    ), Index(
        name = "idx_board_documents_board_id",
        columnList = "board_id"
    ), Index(name = "idx_board_documents_file_id", columnList = "file_id")]
)
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
@SQLDelete(sql = "UPDATE board_documents SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
class BoardDocument : BaseModel() {
    @JoinColumn(name = "board_id", nullable = false)
    @ManyToOne
    lateinit var board: Board

    @Column(name = "name", nullable = false)
    lateinit var name: String

    @JoinColumn(name = "file_id", nullable = false)
    @OneToOne
    lateinit var file: File
}
