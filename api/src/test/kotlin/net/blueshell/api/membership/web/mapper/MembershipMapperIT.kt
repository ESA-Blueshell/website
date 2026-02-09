package net.blueshell.api.membership.web.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.factory.dto.MembershipDTOFactory
import net.blueshell.api.factory.model.MembershipFactory
import net.blueshell.api.membership.web.mapper.MembershipMapper
import net.blueshell.api.membership.persistence.Membership
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class MembershipMapperIT @Autowired constructor(
    private val membershipMapper: MembershipMapper,
    private val membershipDTOFactory: MembershipDTOFactory,
    private val membershipFactory: MembershipFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted membership`() {
            val user = persistUser()
            val membership = persist(membershipFactory.createBasic(user))

            val dto = membershipMapper.toDTO(membership)

            assertThat(dto.id).isEqualTo(membership.id)
            assertThat(dto.userId).isEqualTo(membership.userId)
            assertThat(dto.memberType).isEqualTo(membership.memberType)
            assertThat(dto.startDate).isEqualTo(membership.startDate)
            assertThat(dto.endDate).isEqualTo(membership.endDate)
            assertThat(dto.incasso).isEqualTo(membership.incasso)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists board updates`() {
            authenticateAs(Role.BOARD)
            val user = persistUser()
            val membership = membershipFactory.createBasic(user)
            val dto = membershipDTOFactory.createBasic().apply {
                userId = user.id
            }

            val mapped = membershipMapper.fromDTO(dto, membership)
            val saved = persist(mapped)
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
