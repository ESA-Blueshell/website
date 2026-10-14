package net.blueshell.api.mail.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import net.blueshell.api.shared.model.Identifiable

/** The last UID read from a folder, valid while the folder keeps its UIDVALIDITY. */
@Entity
@Table(name = "inbox_cursor")
class InboxCursor(
    @Id
    @Column(name = "folder", nullable = false)
    val folder: String,
    @Column(name = "uid_validity", nullable = false)
    var uidValidity: Long,
    @Column(name = "last_uid", nullable = false)
    var lastUid: Long,
) : Identifiable<String> {
    override val id: String get() = folder
}
