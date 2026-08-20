package net.blueshell.api.domain.user.persistence

import net.blueshell.api.domain.user.persistence.repository.MemberProfileRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class MemberProfileConditionsAcceptedIT : UserTestSupport() {

    @Autowired
    private lateinit var memberProfiles: MemberProfileRepository

    @Test
    fun `a member profile starts with no recorded acceptance`() {
        val user = assignMemberProfile(createUserWithRole(Role.MEMBER))

        val profile = memberProfiles.findById(user.id!!).orElseThrow()

        assertThat(profile.conditionsAcceptedAt)
            .describedAs("a profile nobody has accepted the conditions on")
            .isNull()
    }

    @Test
    fun `an acceptance timestamp round-trips through the database`() {
        val user = assignMemberProfile(createUserWithRole(Role.MEMBER))
        val acceptedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS)

        transactionTemplate.execute {
            val profile = memberProfiles.findById(user.id!!).orElseThrow()
            profile.conditionsAcceptedAt = acceptedAt
            memberProfiles.save(profile)
        }
        entityManager.clear()

        val reloaded = memberProfiles.findById(user.id!!).orElseThrow()
        assertThat(reloaded.conditionsAcceptedAt)
            .isCloseTo(acceptedAt, within(1, ChronoUnit.SECONDS))
    }

    @Test
    fun `an acceptance can be cleared back to unset`() {
        val user = assignMemberProfile(createUserWithRole(Role.MEMBER))

        transactionTemplate.execute {
            val profile = memberProfiles.findById(user.id!!).orElseThrow()
            profile.conditionsAcceptedAt = Instant.now()
            memberProfiles.save(profile)
        }
        transactionTemplate.execute {
            val profile = memberProfiles.findById(user.id!!).orElseThrow()
            profile.conditionsAcceptedAt = null
            memberProfiles.save(profile)
        }
        entityManager.clear()

        assertThat(memberProfiles.findById(user.id!!).orElseThrow().conditionsAcceptedAt).isNull()
    }

    private fun within(amount: Long, unit: ChronoUnit) =
        org.assertj.core.api.Assertions.within(amount, unit)
}
