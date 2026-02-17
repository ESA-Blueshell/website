package net.blueshell.api.domain.user.web.mapping.response

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.web.dto.response.UserDetailResponse
import net.blueshell.api.domain.user.web.dto.response.UserSummaryResponse
import tech.mappie.api.ObjectMappie

object UserToDetailResponseMapper : ObjectMappie<User, UserDetailResponse>() {
    override fun map(from: User) = mapping {
        UserDetailResponse::roles fromProperty from::inheritedRoles
    }
}

object UserToSummaryResponseMapper : ObjectMappie<User, UserSummaryResponse>()

fun User.asDetailResponse(): UserDetailResponse = UserToDetailResponseMapper.map(this)

fun User.asSummaryResponse(): UserSummaryResponse = UserToSummaryResponseMapper.map(this)
