package net.blueshell.api.committee.persistence

import jakarta.persistence.CascadeType
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.ADDRESS_LENGTH
import net.blueshell.api.shared.model.AuditedAutoIdEntity
import net.blueshell.api.shared.model.DESCRIPTION_MAX
import net.blueshell.api.shared.model.SoftDelete
import net.blueshell.api.shared.model.addressOf
import net.blueshell.api.user.persistence.User
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.Instant

@Entity
@Table(
    name = "committees",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_committees_name_deleted_at", columnNames = ["name", "deleted_at"]),
        UniqueConstraint(name = "uk_committees_slug_deleted_at", columnNames = ["slug", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_committees_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_committees_name", columnList = "name"),
    ],
)
@SQLDelete(sql = "UPDATE committees SET ${SoftDelete.STAMP}, version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction(SoftDelete.ACTIVE)
class Committee(
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "description", nullable = false, length = DESCRIPTION_MAX)
    var description: String,
    /** The address its page answers to. Made from the name unless somebody chose one. */
    @Column(name = "slug", nullable = false, length = ADDRESS_LENGTH)
    var slug: String = addressOf(name),
    /** A committee that no longer runs: kept, with its page and its events, but not offered. */
    @Column(name = "archived", nullable = false)
    var archived: Boolean = false,
    /** Since when it no longer runs; set exactly while [archived] is, which the database checks. */
    @Column(name = "archived_at")
    var archivedAt: Instant? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "banner_file_id")
    var banner: File? = null,
    /** Its logo, drawn beside its name and in place of it in a row of marks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "icon_file_id")
    var icon: File? = null,
) : AuditedAutoIdEntity() {
    /** The codes of the games the committee organises events for. */
    @ElementCollection
    @CollectionTable(name = "committee_games", joinColumns = [JoinColumn(name = "committee_id")])
    @Column(name = "game_code", nullable = false, length = 32)
    @BatchSize(size = 50)
    val gameCodes: MutableSet<String> = linkedSetOf()

    @OneToMany(mappedBy = "committee", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _members: MutableList<CommitteeMember> = mutableListOf()
    val members: List<CommitteeMember>
        get() = _members

    fun hasMember(user: User?): Boolean = hasMember(user?.id)

    fun hasMember(userId: Long?): Boolean = userId != null && _members.any { cm -> cm.user.id == userId }

    fun replaceMembers(members: List<CommitteeMember>) {
        _members.clear()
        _members.addAll(members)
        _members.forEach { it.committee = this }
    }
}
