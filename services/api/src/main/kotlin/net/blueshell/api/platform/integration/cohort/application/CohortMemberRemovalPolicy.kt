package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.stereotype.Component

enum class CohortMemberRemovalOrigin {
    AUTOMATIC_SYNC,
    EXPLICIT_OPERATOR,
}

@Component
class CohortMemberRemovalPolicy {
    fun allows(system: TargetSystem, origin: CohortMemberRemovalOrigin): Boolean =
        origin == CohortMemberRemovalOrigin.EXPLICIT_OPERATOR || system != TargetSystem.BREVO
}
