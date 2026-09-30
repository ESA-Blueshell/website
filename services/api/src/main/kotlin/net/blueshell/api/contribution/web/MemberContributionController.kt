package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.contribution.domain.MemberContributions
import net.blueshell.api.contribution.domain.MemberPeriodContribution
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Contributions")
class MemberContributionController(
    private val contributions: MemberContributions,
) {
    @GetMapping("/users/{userId}/contributions")
    @BoardOnly
    fun findMemberContributions(
        @PathVariable userId: Long,
    ): List<MemberPeriodContribution> = contributions.of(userId)
}
