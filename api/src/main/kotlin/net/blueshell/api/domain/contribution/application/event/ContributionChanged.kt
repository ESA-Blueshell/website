package net.blueshell.api.domain.contribution.application.event

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class ContributionChanged(
    val userId: Long,
    val periodId: Long,
    val changeType: ContributionChange,
    override val actor: Actor = Actor.system()
) : ActorTracked
