package net.blueshell.api.cohort.domain

import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.shared.job.NonRetryableJobException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class TargetStrategies(strategies: List<TargetStrategy>) {
    private val bySystem = strategies.associateBy { it.system }

    fun require(system: TargetSystem): TargetStrategy =
        bySystem[system]
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$system is not a cohort target")

    /**
     * The strategy a job needs. A missing one is terminal rather than retried: no amount of
     * backoff registers a bean, where [require] answers a request with a bad request.
     */
    fun requireForJob(system: TargetSystem): TargetStrategy =
        bySystem[system]
            ?: throw NonRetryableJobException("No TargetStrategy registered for $system")

    fun descriptor(system: TargetSystem): TargetDescriptor = require(system).descriptor

    fun descriptors(): List<TargetDescriptor> =
        bySystem.values.map { it.descriptor }.sortedBy { it.systemLabel }
}
