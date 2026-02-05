package net.blueshell.api.model.committee

import jakarta.persistence.*
import net.blueshell.api.base.entity.AuditedAutoIdEntity
import net.blueshell.api.model.User
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction

@Entity
@Table(
    name = "committees",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_committees_name_deleted_at", columnNames = ["name", "deleted_at"]),
    ],
    indexes = [
        Index(name = "idx_committees_deleted_at", columnList = "deleted_at"),
        Index(name = "idx_committees_name", columnList = "name"),
    ]
)
@SQLDelete(sql = "UPDATE committees SET deleted_at = NOW(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at = '9999-12-31 23:59:59'")
class Committee : AuditedAutoIdEntity() {
    @Column(name = "name", nullable = false)
    lateinit var name: String

    @Column(name = "description", nullable = false, length = 4095)
    lateinit var description: String

    @OneToMany(mappedBy = "_committee", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    private val _members: MutableSet<CommitteeMember> = linkedSetOf()
    var members: Set<CommitteeMember>
        get() = _members
        set(newMembers) {
            _members.clear()
            _members.addAll(newMembers)
            val committeeId = id
            if (committeeId != null) {
                _members.forEach { it.committeeId = committeeId }
            }
        }

    fun hasMember(user: User?): Boolean {
        return user != null && _members.any { cm -> cm.user.id == user.id }
    }

    fun addMember(member: CommitteeMember) {
        val committeeId = id
        if (committeeId != null) {
            member.committeeId = committeeId
        }
        _members.add(member)
    }
}
