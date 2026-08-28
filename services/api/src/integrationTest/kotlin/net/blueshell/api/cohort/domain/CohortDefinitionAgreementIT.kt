package net.blueshell.api.cohort.domain

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * The two answers every definition gives must be the same answer.
 *
 * A definition says who belongs in bulk, for recomputing a whole cohort, and whether one
 * member belongs, for when something about them changes. Nothing forces those to agree except
 * this: run every definition the registry produces against every member the database holds,
 * and require that the set and the predicate pick the same people. A definition that
 * implements one and forgets the other is caught here rather than by a mailing list quietly
 * going wrong.
 */
@SpringBootTest
class CohortDefinitionAgreementIT : UserTestSupport() {
    @Autowired
    private lateinit var definitions: CohortDefinitionRegistry

    @Autowired
    private lateinit var users: UserService

    @Test
    fun `every definition agrees with itself about who belongs`() {
        // Somebody to be found: a member, so the period and newsletter cohorts have a
        // candidate, and the sources have something to disagree about.
        val member = createUserWithRole(Role.MEMBER)
        val everyone = users.findAll().mapNotNull { it.id }
        assertThat(everyone).contains(member.id)

        val all = definitions.all()
        assertThat(all).isNotEmpty

        all.forEach { definition ->
            val inBulk = definition.members()
            val oneByOne = everyone.filterTo(mutableSetOf()) { definition.contains(it) }

            assertThat(inBulk)
                .describedAs(
                    "%s: members() and contains() disagree about %s",
                    definition.key,
                    (inBulk - oneByOne) + (oneByOne - inBulk),
                )
                .isEqualTo(oneByOne)
        }
    }

    @Test
    fun `a deleted member belongs to nothing, whatever the definitions say`() {
        val member = createUserWithRole(Role.MEMBER)
        val id = member.id!!

        // Before: whatever they belong to is what the definitions say they belong to.
        val claimedBefore = definitions.all().filter { it.contains(id) }
        assertThat(definitions.definitionsFor(id).map { it.key })
            .containsExactlyInAnyOrderElementsOf(claimedBefore.map { it.key })

        users.deleteById(id)

        // After: nothing claims them, and no definition's own members include them either.
        assertThat(definitions.definitionsFor(id)).isEmpty()
        definitions.all().forEach { definition ->
            assertThat(definitions.membersOf(definition))
                .describedAs("%s still holds a deleted member", definition.key)
                .doesNotContain(id)
        }
    }
}
