package net.blueshell.api.domain.sponsor.command.result

import java.time.Instant

/**
 * Command result model for Sponsor domain.
 * Matches the structure of SponsorResponse DTO (1:1 mapping).
 *
 * This model sits between the persistence layer (Sponsor entity) and the web layer (SponsorResponse),
 * ensuring commands don't directly return persistence entities (ADR-016 strict clean architecture).
 */
data class SponsorResult(
    val id: Long?,
    val name: String,
    val description: String,
    val version: Long?,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val deletedAt: Instant?
)
