package net.blueshell.api.cohort.domain

import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.discord.api.LinkedDiscordRoles
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** The Discord roles linked to a cohort, which are the ones the site keeps. */
@Component
class CohortLinkedRoles(
    private val targets: TargetRepository,
    private val targetIds: CohortTargetIds,
) : LinkedDiscordRoles {
    @Transactional(readOnly = true)
    override fun ids(): Set<String> = targets.findAllBySystem(TargetSystem.DISCORD.name).mapNotNull(targetIds::find).toSet()
}
