package net.blueshell.api.vote.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.AutoIdEntity

/** A game somebody picked, copied from IGDB with our own copy of its cover, if one could be made. */
@Entity
@Table(name = "vote_candidates")
class VoteCandidate(
    @Column(name = "igdb_id", nullable = false, unique = true)
    val igdbId: Long,
    @Column(name = "name", nullable = false)
    var name: String,
    @Column(name = "first_release_year")
    var firstReleaseYear: Int? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cover_file_id")
    var cover: File? = null,
) : AutoIdEntity()
