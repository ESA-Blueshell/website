package net.blueshell.api.file.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity

/** Bytes a changeset stopped pointing at, which only the api can reach to delete. */
@Entity
@Table(name = "blobs_to_delete")
class BlobToDelete(
    @Column(name = "path", nullable = false, columnDefinition = "LONGTEXT")
    val path: String,
) : AutoIdEntity()
