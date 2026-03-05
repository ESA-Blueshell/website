package net.blueshell.api.platform.integration.contact.persistence

import jakarta.persistence.*
import net.blueshell.api.shared.model.Identifiable

@Entity
@Table(name = "listmonk_lists")
class ListmonkList(
    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "list_id")
    var list: ContactList,

    @Column(name = "external_id", nullable = false)
    var externalId: Long,
) : Identifiable<Long> {
    @Id
    var listId: Long = 0

    override val id: Long? get() = listId
}
