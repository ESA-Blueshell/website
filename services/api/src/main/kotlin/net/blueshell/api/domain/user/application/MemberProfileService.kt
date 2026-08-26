package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.repository.MemberProfileRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberProfileService(repository: MemberProfileRepository) :
    BaseModelService<MemberProfile, Long, MemberProfileRepository>(repository) {

    /**
     * Which of these members allow their real name on the team pages.
     *
     * A member who never said so is simply absent from the answer, whether they turned it off
     * or never had a profile to turn it on in.
     */
    @Transactional(readOnly = true)
    fun consentingToNameOnTeamPages(userIds: Collection<Long>): Set<Long> =
        if (userIds.isEmpty()) {
            emptySet()
        } else {
            repository.findUserIdsConsentingToNameOnTeamPages(userIds).toSet()
        }
}
