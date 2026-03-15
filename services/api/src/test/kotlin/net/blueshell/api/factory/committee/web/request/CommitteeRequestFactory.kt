package net.blueshell.api.factory.committee.web.request

import org.springframework.stereotype.Component

@Component
class CommitteeRequestFactory {
    data class MemberInput(
        val userId: Long,
        val role: String
    )

    fun createPayload(
        name: String,
        description: String,
        members: List<MemberInput>
    ): String =
        """{"name":"$name","description":"$description","members":[${membersJson(members)}]}"""

    fun updatePayload(
        version: Long,
        name: String,
        description: String,
        members: List<MemberInput>
    ): String =
        """{"name":"$name","description":"$description","members":[${membersJson(members)}],"version":$version}"""

    private fun membersJson(members: List<MemberInput>): String =
        members.joinToString(",") { """{"userId":${it.userId},"role":"${it.role}"}""" }
}
