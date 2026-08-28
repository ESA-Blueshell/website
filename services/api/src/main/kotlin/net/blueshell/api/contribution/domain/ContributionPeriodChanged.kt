package net.blueshell.api.contribution.domain

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class ContributionPeriodChanged(
    val periodId: Long,
    override val actor: Actor = Actor.system()
) : ActorTracked
