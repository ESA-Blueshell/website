package net.blueshell.api.contribution.api

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked
import net.blueshell.api.contribution.domain.ContributionChange

data class ContributionChanged(
    val userId: Long,
    val periodId: Long,
    val changeType: ContributionChange,
    override val actor: Actor = Actor.system()
) : ActorTracked
