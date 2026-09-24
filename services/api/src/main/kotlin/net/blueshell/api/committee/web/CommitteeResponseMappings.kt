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

fun Committee.asDetailResponse(): CommitteeDetailResponse =
    CommitteeDetailResponse(
        id = this.id!!,
        name = this.name,
        description = this.description,
        slug = this.slug,
        listed = this.listed,
        archived = this.archived,
        banner = this.banner?.asImage(),
        gameCodes = this.gameCodes.sorted(),
        members = this.members.map { it.asDto() }.toMutableList(),
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Committee.asSummaryResponse(): CommitteeSummaryResponse =
    CommitteeSummaryResponse(
        id = this.id!!,
        name = this.name,
        description = this.description,
        slug = this.slug,
        listed = this.listed,
        archived = this.archived,
        banner = this.banner?.asImage(),
        gameCodes = this.gameCodes.sorted(),
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
        listed = this.listed,
        archived = this.archived,
        banner = this.banner?.asImage(),
        gameCodes = this.gameCodes.sorted(),
        members = seats.map { CommitteeSeatResponse(discordTag = it.discordTag, avatar = it.avatar, role = it.role) },
    )
