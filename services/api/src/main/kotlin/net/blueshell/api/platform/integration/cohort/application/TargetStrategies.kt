package net.blueshell.api.platform.integration.cohort.application

import net.blueshell.api.platform.integration.cohort.port.out.TargetDescriptor
import net.blueshell.api.platform.integration.cohort.port.out.TargetStrategy
import net.blueshell.api.shared.enums.TargetSystem
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class TargetStrategies(strategies: List<TargetStrategy>) {
    private val bySystem = strategies.associateBy { it.system }

    fun require(system: TargetSystem): TargetStrategy =
        bySystem[system]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$system is not a cohort target")

    fun descriptor(system: TargetSystem): TargetDescriptor = require(system).descriptor

    fun descriptors(): List<TargetDescriptor> =
        bySystem.values.map { it.descriptor }.sortedBy { it.systemLabel }
}
