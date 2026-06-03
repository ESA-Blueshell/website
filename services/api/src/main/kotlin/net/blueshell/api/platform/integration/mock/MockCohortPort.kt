package net.blueshell.api.platform.integration.mock

import net.blueshell.api.platform.integration.cohort.port.out.CohortPort
import net.blueshell.api.platform.integration.cohort.port.out.MemberRef
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory [CohortPort] for test and dev profiles. Holds member and
 * cohort state so the registry, drift service, and controller ITs
 * have a real port to exercise without touching Brevo.
 *
 * Reported system is [TargetSystem.BREVO] so tests exercise the Brevo
 * code path without needing the production adapter.
 */
@Service
@Primary
@Profile("test | dev")
class MockCohortPort : CohortPort {

    override val system: TargetSystem = TargetSystem.BREVO

    // cohortId → label
    private val cohorts = ConcurrentHashMap<String, String>()

    // (externalUserId, externalCohortId) → email hint
    private val memberships = ConcurrentHashMap<Pair<String, String>, String?>()

    private val idSequence = AtomicLong(9000)

    override fun createCohort(label: String, hint: String?): String {
        val id = idSequence.getAndIncrement().toString()
        cohorts[id] = label
        log.info("MockCohort: created cohort id={} label='{}'", id, label)
        return id
    }

    override fun addMember(externalUserId: String, externalCohortId: String) {
        memberships[externalUserId to externalCohortId] = null
        log.info("MockCohort: added member {} to cohort {}", externalUserId, externalCohortId)
    }

    override fun removeMember(externalUserId: String, externalCohortId: String) {
        memberships.remove(externalUserId to externalCohortId)
        log.info("MockCohort: removed member {} from cohort {}", externalUserId, externalCohortId)
    }

    override fun deleteCohort(externalCohortId: String) {
        cohorts.remove(externalCohortId)
        memberships.keys.removeIf { (_, cohortId) -> cohortId == externalCohortId }
        log.info("MockCohort: deleted cohort {}", externalCohortId)
    }

    override fun listMembers(externalCohortId: String): List<MemberRef> =
        memberships.keys
            .filter { (_, cohortId) -> cohortId == externalCohortId }
            .map { (userId, _) -> MemberRef(externalUserId = userId, label = memberships[userId to externalCohortId]) }

    // ── Test helpers ──────────────────────────────────────────────────────────

    /** Directly seeds a member with an optional email label for drift tests. */
    fun seedMember(externalUserId: String, externalCohortId: String, label: String? = null) {
        memberships[externalUserId to externalCohortId] = label
    }

    fun getMembers(externalCohortId: String): Set<String> =
        memberships.keys.filter { (_, cId) -> cId == externalCohortId }.map { (uId, _) -> uId }.toSet()

    fun clear() {
        cohorts.clear()
        memberships.clear()
    }

    companion object {
        private val log = LoggerFactory.getLogger(MockCohortPort::class.java)
    }
}
