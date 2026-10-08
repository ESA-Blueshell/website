package net.blueshell.api.committee.web

import net.blueshell.api.committee.domain.CommitteeSeat
import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.file.api.asImage

fun CommitteeMember.asDto(): CommitteeMemberResponse =
    CommitteeMemberResponse(
        userId = this.userId,
        committeeId = this.committeeId,
        role = this.role,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

/** The committee, with who sits on it only where [withMembers] says the reader may know. */
fun Committee.asResponse(withMembers: Boolean = true): CommitteeResponse =
    CommitteeResponse(
        id = this.id!!,
        name = this.name,
        description = this.description,
        slug = this.slug,
        archived = this.archived,
        archivedAt = this.archivedAt,
        banner = this.banner?.asImage(),
        icon = this.icon?.asImage(),
        gameCodes = this.gameCodes.sorted(),
        members = if (withMembers) this.members.map { it.asDto() } else null,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Committee.asPageResponse(seats: List<CommitteeSeat>): CommitteePageResponse =
    CommitteePageResponse(
        id = this.id!!,
        name = this.name,
        slug = this.slug,
        description = this.description,
        archived = this.archived,
        archivedAt = this.archivedAt,
        banner = this.banner?.asImage(),
        icon = this.icon?.asImage(),
        gameCodes = this.gameCodes.sorted(),
        members = seats.map { CommitteeSeatResponse(name = it.name, avatar = it.avatar, discord = it.discord, role = it.role) },
    )
