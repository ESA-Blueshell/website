package net.blueshell.api.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import jakarta.persistence.Entity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.readText

/**
 * A unit test builds its entities with `Entities` rather than mocking them: a mocked entity
 * answers only what was stubbed, so a rule that reads one more field passes on a null the entity
 * could never hold.
 *
 * Read from the sources, since a mock of an entity leaves nothing in the bytecode an ArchUnit rule
 * can tell from any other use of its class. The files that still mock one are pinned with how many
 * they hold, so the count only falls; each is a line here, and clearing one is a visible diff.
 */
class EntityMockArchitectureTest {
    private companion object {
        val PINNED =
            mapOf(
                "auth/domain/PasswordRecoveryServiceTest.kt" to 3,
                "auth/domain/RecoveryEmailJobTest.kt" to 1,
                "auth/domain/RecoveryTokenFactoryTest.kt" to 1,
                "auth/domain/SignupTokenServiceTest.kt" to 1,
                "auth/domain/UserActivationServiceTest.kt" to 1,
                "board/api/BoardMemberServiceTest.kt" to 2,
                "board/domain/ShippedBoardArtTest.kt" to 4,
                "cohort/domain/CohortLedgerTest.kt" to 2,
                "cohort/domain/CohortMembershipSyncServiceTest.kt" to 1,
                "cohort/domain/CohortMembershipUpdaterTest.kt" to 3,
                "cohort/domain/CohortSubjectQueryServiceTest.kt" to 1,
                "cohort/domain/CohortTargetingServiceTest.kt" to 9,
                "cohort/domain/CohortVerificationSchedulerTest.kt" to 2,
                "cohort/domain/InboundReconcileTest.kt" to 1,
                "cohort/persistence/CohortMemberStateTest.kt" to 2,
                "committee/api/CommitteeMemberServiceTest.kt" to 1,
                "committee/domain/CommitteeSeatRevocationListenerTest.kt" to 2,
                "committee/domain/CommitteeSeatsTest.kt" to 1,
                "committee/persistence/CommitteeTest.kt" to 2,
                "committee/web/CommitteeControllerTest.kt" to 2,
                "contribution/api/ContributionServiceTest.kt" to 2,
                "contribution/api/ContributionServicesWriteTest.kt" to 6,
                "contribution/domain/ContributionAsksQueueTheirEmailTest.kt" to 2,
                "email/domain/EmailServiceWriteTest.kt" to 4,
                "esports/domain/ShippedTeamArtTest.kt" to 4,
                "event/api/EventPostsTest.kt" to 8,
                "event/domain/EventJobsListenerUnitTest.kt" to 2,
                "event/domain/EventReadServicesTest.kt" to 2,
                "event/domain/EventSignUpServiceTest.kt" to 1,
                "event/domain/EventUseCasesTest.kt" to 4,
                "event/web/EventSignUpControllerTest.kt" to 1,
                "file/api/ShippedPicturesTest.kt" to 1,
                "file/domain/ImageRenditionsJobTest.kt" to 2,
                "file/domain/ImageRenditionsTest.kt" to 1,
                "jobs/web/JobExecutionViewServiceTest.kt" to 1,
                "security/GuestPermissionTest.kt" to 2,
                "security/OwnershipPermissionEvaluatorsTest.kt" to 2,
                "sync/domain/CalendarSyncServiceTest.kt" to 1,
                "sync/domain/ContactSyncServiceTest.kt" to 1,
                "telemetry/domain/TelemetryServiceTest.kt" to 1,
                "user/api/UserServicesWriteTest.kt" to 6,
                "user/web/MembershipResponseMappingsTest.kt" to 2,
            )
    }

    private val entities =
        ClassFileImporter()
            .importPath(Paths.get("build/classes/kotlin/main"))
            .filter { it.isAnnotatedWith(Entity::class.java) }
            .map { it.simpleName }

    private val pattern =
        entities.joinToString("|").let {
            Regex("""\b(?:mock|mockk|spy|spyk)<($it)>|:\s*($it)\??\s*=\s*(?:mock|mockk)\b|(?:mock|mockk)\(($it)::class""")
        }

    private fun measured(): Map<String, Int> {
        val root = Paths.get("src/test/kotlin/net/blueshell/api")
        return Files.walk(root).use { paths ->
            paths
                .filter { it.extension == "kt" }
                .toList()
                .associate { root.relativize(it).toString() to pattern.findAll(it.readText()).count() }
                .filterValues { it > 0 }
        }
    }

    @Test
    fun `no unit test mocks an entity it was not already mocking`() {
        assertThat(entities).describedAs("the entity classes this rule reads for").isNotEmpty

        val grown = measured().filter { (file, count) -> count > (PINNED[file] ?: 0) }

        assertThat(grown)
            .describedAs("build these entities with testsupport.Entities rather than mocking them")
            .isEmpty()
    }

    @Test
    fun `no pinned file holds fewer mocks than its pin says`() {
        val measured = measured()

        val stale = PINNED.filter { (file, count) -> (measured[file] ?: 0) < count }

        assertThat(stale).describedAs("lower or remove these pins, so the count cannot climb back").isEmpty()
    }
}
