package net.blueshell.api.user.api

import net.blueshell.api.auth.domain.RewrapSealedValuesJob
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ExplainedJobFailure
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.domain.AddressUseCases
import net.blueshell.api.user.domain.SealedValueRewrapScheduler
import net.blueshell.api.user.domain.sealing.LocalSealer
import net.blueshell.api.user.domain.sealing.keyVersionOf
import net.blueshell.api.user.persistence.Address
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/** After a key is rotated, the nightly rewrap leaves every sealed value on the newest version. */
class SealedValueRewrapIT : UserTestSupport() {
    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @Autowired
    private lateinit var sealer: LocalSealer

    @Autowired
    private lateinit var job: RewrapSealedValuesJob

    @Autowired
    private lateinit var scheduler: SealedValueRewrapScheduler

    @Autowired
    private lateinit var addressUseCases: AddressUseCases

    @Autowired
    private lateinit var sealedAddresses: SealedAddresses

    private val payload get() = mapper.writeValueAsString(UserJobs.RewrapSealedValuesPayload())

    private fun saveAddress(): Long =
        addressUseCases.create(createUserWithRole(Role.MEMBER).id!!, "NL", "Enschede", "Hallenweg", "5", "7522NH").id!!

    private fun sealedOf(id: Long): String =
        jdbc.queryForObject("SELECT sealed_address FROM addresses WHERE id = ?", String::class.java, id)!!

    private fun everySealed(): List<String> =
        jdbc
            .queryForList(
                "SELECT sealed_address FROM addresses WHERE sealed_address IS NOT NULL ORDER BY id",
                String::class.java,
            ).filterNotNull()

    @Test
    fun `a run after a rotation moves every sealed value, soft-deleted ones too, and a second run changes nothing`() {
        val live = saveAddress()
        val deleted = saveAddress()
        jdbc.update("UPDATE addresses SET deleted_at = NOW() WHERE id = ?", deleted)
        val newest = sealer.rotate("api-address")
        assertThat(keyVersionOf(sealedOf(live))).isLessThan(newest)

        assertThat(job.handle(payload, null, false)).isInstanceOf(JobOutcome.Done::class.java)

        assertThat(everySealed().map(::keyVersionOf)).hasSize(2).containsOnly(newest)
        assertThat(sealedAddresses.open(addressOf(live))?.street).isEqualTo("Hallenweg")

        val after = everySealed()
        assertThat(job.handle(payload, null, false)).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat(everySealed()).isEqualTo(after)
    }

    @Test
    fun `a value that does not rewrap is named, the rest still move, and the next run tries it again`() {
        val sound = saveAddress()
        val broken = saveAddress()
        // Another member's value: it does not open under this member, so it cannot be moved.
        jdbc.update("UPDATE addresses SET sealed_address = ? WHERE id = ?", sealedOf(sound), broken)
        val newest = sealer.rotate("api-address")

        repeat(2) {
            assertThatThrownBy { job.handle(payload, null, false) }
                .isInstanceOf(ExplainedJobFailure::class.java)
                .hasMessageContaining("address $broken.")
        }

        assertThat(keyVersionOf(sealedOf(sound))).isEqualTo(newest)
        assertThat(keyVersionOf(sealedOf(broken))).isLessThan(newest)
    }

    @Test
    fun `the nightly run is queued as a scheduled run`() {
        scheduler.rewrapSealedValues()

        assertThat(
            jobExecutions.findByJobType(UserJobs.RewrapSealedValues.type).map { it.trigger },
        ).containsExactly(JobTrigger.SCHEDULED_RUN)
    }

    private fun addressOf(id: Long): Address =
        transactionTemplate.execute { entityManager.find(Address::class.java, id).also { it.user.id } }!!
}
