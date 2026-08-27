package net.blueshell.api.domain.sponsor.application.result

import net.blueshell.api.domain.sponsor.persistence.Sponsor

/**
 * Mappings from Sponsor entity to SponsorResult.
 * Used by command handlers to convert persistence entities to command results.
 */
fun Sponsor.toResult(): SponsorResult = SponsorResult(
    id = this.id!!,
    name = this.name,
    description = this.description,
    version = this.version,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
    deletedAt = this.deletedAt
)

fun List<Sponsor>.toResults(): List<SponsorResult> = this.map { it.toResult() }
