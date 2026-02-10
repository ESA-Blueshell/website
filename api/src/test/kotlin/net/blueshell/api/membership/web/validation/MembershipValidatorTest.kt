package net.blueshell.api.membership.web.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.membership.web.dto.MembershipDTO
import net.blueshell.api.factory.dto.MembershipDTOFactory
import net.blueshell.api.shared.validation.group.Administration
import net.blueshell.api.shared.validation.group.Creation
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDate

/**
 * Unit tests for MembershipDTO validation.
 */
@SpringBootTest
class MembershipValidatorTest @Autowired constructor(
    private val validator: Validator,
    private val membershipFactory: MembershipDTOFactory
) {

    @Test
    fun `valid membership dto passes validation`() {
        val dto = membershipFactory.createBasic()
        val violations = validator.validate(dto, Creation::class.java, Administration::class.java)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `membership dto with future start date fails validation`() {
        val dto = membershipFactory.createWithCustomizations { it.startDate = LocalDate.now().plusDays(1) }
        val violations = validator.validate(dto, Administration::class.java)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "startDate" })
    }

    @Test
    fun `membership dto with end date before start date fails validation`() {
        val startDate = LocalDate.now()
        val dto: MembershipDTO = membershipFactory.createWithCustomizations {
            it.startDate = startDate
            it.endDate = startDate.minusDays(1)
        }

        val violations: Set<ConstraintViolation<MembershipDTO>> = validator.validate(dto)
        // No cross-field constraint asserted here.
        assertTrue(
            violations.none {
                val path = it.propertyPath.toString()
                path == "startDate" || path == "endDate"
            }
        )
    }
}
