package net.blueshell.api.domain.membership.web.dto

import net.blueshell.api.domain.membership.application.MembershipService
import net.blueshell.api.domain.membership.persistence.Membership
import net.blueshell.api.domain.membership.web.mapping.asEntity
import net.blueshell.api.factory.dto.MembershipDTOFactory
import net.blueshell.api.factory.model.MembershipFactory
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MembershipDtoIT @Autowired constructor(
    private val membershipDTOFactory: MembershipDTOFactory,
    private val membershipFactory: MembershipFactory,
    private val membershipService: MembershipService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists board updates`() {
            authenticateAs(Role.BOARD)
            val user = persistUser()
            val membership = membershipFactory.createBasic(user)
            val dto = membershipDTOFactory.createBasic().apply {
                userId = user.id
            }

            val mapped = dto.asEntity(membership)
            val saved = membershipService.create(mapped)
            flushAndClear()

            val reloaded = reload(Membership::class.java, saved.id!!)

            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.memberType).isEqualTo(dto.memberType)
            assertThat(reloaded.startDate).isEqualTo(dto.startDate)
            assertThat(reloaded.endDate).isEqualTo(dto.endDate)
            assertThat(reloaded.incasso).isEqualTo(dto.incasso)
        }
    }
}
