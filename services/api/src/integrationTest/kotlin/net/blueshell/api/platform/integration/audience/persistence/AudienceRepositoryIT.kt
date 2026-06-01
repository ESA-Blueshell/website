package net.blueshell.api.platform.integration.audience.persistence

import net.blueshell.api.platform.integration.audience.persistence.repository.AudienceMemberRepository
import net.blueshell.api.platform.integration.audience.persistence.repository.AudienceRepository
import net.blueshell.api.platform.integration.audience.persistence.repository.AudienceRuleRepository
import net.blueshell.api.platform.integration.sync.port.TargetSystem
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Round-trip checks that the V66 schema and the Audience / AudienceMember /
 * AudienceRule entities agree on column names and types. With
 * `hibernate.ddl-auto=none` Hibernate cannot fail at startup on a mismatch,
 * so this IT is the earliest place a typo here will surface.
 */
@SpringBootTest
class AudienceRepositoryIT : UserTestSupport() {

    @Autowired
    private lateinit var audiences: AudienceRepository

    @Autowired
    private lateinit var audienceMembers: AudienceMemberRepository

    @Autowired
    private lateinit var audienceRules: AudienceRuleRepository

    @Test
    fun `audience persists and reloads with all configured fields`() {
        val audience = Audience(
            system = TargetSystem.BREVO.name,
            kind = AudienceGroupKind.LIST,
            label = "Members",
        )

        val saved = audiences.save(audience)
        val reloaded = audiences.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.system).isEqualTo(TargetSystem.BREVO.name)
        assertThat(reloaded.kind).isEqualTo(AudienceGroupKind.LIST)
        assertThat(reloaded.label).isEqualTo("Members")
        assertThat(reloaded.isSoftDeleted).isFalse()
    }

    @Test
    fun `audience filters by system and kind`() {
        audiences.save(Audience(TargetSystem.BREVO.name, AudienceGroupKind.LIST, "brevo-list"))
        audiences.save(Audience(TargetSystem.GOOGLE_CALENDAR.name, AudienceGroupKind.GROUP, "g-group"))

        assertThat(audiences.findAllBySystem(TargetSystem.BREVO.name))
            .extracting<String> { it.label }
            .contains("brevo-list")
        assertThat(audiences.findAllBySystemAndKind(TargetSystem.BREVO.name, AudienceGroupKind.ROLE))
            .isEmpty()
    }

    @Test
    fun `audience member round-trips with FK to audience and user_id`() {
        val user = createUserWithRole(Role.MEMBER)
        val audience = audiences.save(Audience(TargetSystem.BREVO.name, AudienceGroupKind.LIST, "Members"))

        val saved = audienceMembers.save(AudienceMember(audience = audience, userId = user.id!!))
        val reloaded = audienceMembers.findById(saved.id!!).orElseThrow()

        assertThat(reloaded.audience.id).isEqualTo(audience.id)
        assertThat(reloaded.userId).isEqualTo(user.id)

        assertThat(audienceMembers.findAllByUserId(user.id!!)).hasSize(1)
        assertThat(audienceMembers.findAllByAudienceId(audience.id!!)).hasSize(1)
        assertThat(audienceMembers.findByAudienceIdAndUserId(audience.id!!, user.id!!)?.id)
            .isEqualTo(saved.id)
    }

    @Test
    fun `audience rule lookup by fact returns enabled rows only`() {
        val audience = audiences.save(Audience(TargetSystem.BREVO.name, AudienceGroupKind.LIST, "Members"))

        audienceRules.save(
            AudienceRule(
                factKind = AudienceFactKind.ROLE,
                factKey = Role.MEMBER.name,
                audience = audience,
                enabled = true,
            )
        )
        audienceRules.save(
            AudienceRule(
                factKind = AudienceFactKind.ROLE,
                factKey = Role.BOARD.name,
                audience = audience,
                enabled = false,
            )
        )

        val memberRules =
            audienceRules.findAllByFactKindAndFactKeyAndEnabledTrue(AudienceFactKind.ROLE, Role.MEMBER.name)
        val boardRules =
            audienceRules.findAllByFactKindAndFactKeyAndEnabledTrue(AudienceFactKind.ROLE, Role.BOARD.name)

        assertThat(memberRules).hasSize(1)
        assertThat(boardRules).isEmpty()
        assertThat(audienceRules.findAllByAudienceId(audience.id!!)).hasSize(2)
    }
}
