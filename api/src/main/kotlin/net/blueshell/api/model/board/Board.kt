package net.blueshell.api.model.board

import jakarta.persistence.*
import net.blueshell.api.model.File
import net.blueshell.api.model.base.AuditedAutoIdEntity
import net.blueshell.api.model.base.asRef
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
    @field:JoinColumn(name = "picture_id")
    @field:OneToOne(cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private var _picture: File? = null
    var picture: File?
        get() = _picture
        set(value) {
            _picture = value
            pictureId = value?.id
        }

    @field:Column(name = "picture_id", updatable = false, insertable = false)
    var pictureId: Long? = null
        get() = _picture?.id
        set(value) {
            field = value
            if (value == null) {
                _picture = null
            } else if (value != _picture?.id) {
                _picture = File::class.asRef(value)
            }
        }

    @OneToMany(mappedBy = "_board", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    private val _members: MutableSet<BoardMember> = linkedSetOf()
    val members: Set<BoardMember>
        get() = _members

    @OneToMany(mappedBy = "_board", fetch = FetchType.LAZY)
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
}
