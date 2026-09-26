package net.blueshell.api.user.api

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.sql.Date

class MemberProfileCompletenessTest {
    private val validator = Validation.buildDefaultValidatorFactory().validator
    private val bare = UpsertMemberProfileData(null, null, null, " ", bhv = false, ehbo = false)
    private val whole = bare.copy(dateOfBirth = Date.valueOf("2000-01-02"), nationality = "NL")

    private fun refused(candidate: MemberProfileCompleteness) = validator.validate(candidate).map { it.propertyPath.toString() }

    @Test
    fun `a member's profile names a date of birth and a nationality`() {
        assertThat(refused(bare.completenessFor(member = true)))
            .containsExactlyInAnyOrder("memberProfile.dateOfBirth", "memberProfile.nationality")
        assertThat(refused(bare.copy(nationality = "NL").completenessFor(member = true))).containsExactly("memberProfile.dateOfBirth")
        assertThat(refused(whole.completenessFor(member = true))).isEmpty()
    }

    @Test
    fun `anybody else's profile may leave both out`() {
        assertThat(refused(bare.completenessFor(member = false))).isEmpty()
        assertThat(CompleteForMembersValidator().isValid(null, org.mockito.kotlin.mock())).isTrue()
    }
}
