package net.blueshell.api.architecture

import net.blueshell.api.architecture.support.ArchJUnitTestBase
import net.blueshell.api.architecture.support.ArchModules
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Architecture ADR-003: a module's `web` package is controllers, request and
 * response types and their mappers. It is internal — nothing outside the module
 * calls a controller, and a response type is shaped for one endpoint's payload,
 * not for another module to reuse. What a module publishes goes through its `api`
 * surface instead.
 *
 * ADR-003 records the reaches that exist as "a defect rather than a surface: they
 * are inverted or copied, not published". They are pinned in [PINNED] rather than
 * fixed here, because the cleanup is sequenced separately and this rule exists to
 * stop the set growing while that runs. Every entry is a line in the file, so
 * dropping one is a visible diff.
 *
 * Pinned at 32 module-to-type reaches, naming 21 distinct types across 12
 * consuming modules and 82 reaching classes. `const val` references are inlined
 * by the Kotlin compiler and leave no dependency for ArchUnit to see, so a reach
 * that only reads a constant — `SecurityConfig` reading
 * `SignupController.SIGNUP_TOKEN_HEADER` — is invisible to this rule.
 */
class CrossModuleWebAccessArchitectureTest : ArchJUnitTestBase(ArchitecturePackages.ROOT) {

    private companion object {
        /**
         * Reaches into another module's web package that existed when this rule
         * landed, as `<consuming module> -> <type reached>`.
         */
        val PINNED = setOf(
            "auth -> net.blueshell.api.telemetry.web.RedirectResponse",
            "auth -> net.blueshell.api.user.web.CreateUserRequest",
            "auth -> net.blueshell.api.user.web.UpsertMemberProfileRequest",
            "auth -> net.blueshell.api.user.web.SignupOutcomeResponse",
            "auth -> net.blueshell.api.user.web.MemberProfileRequestMappingsKt",
            "auth -> net.blueshell.api.user.web.UserRequestMappingsKt",
            "blog -> net.blueshell.api.shared.web.BaseController",
            "cohort -> net.blueshell.api.jobs.web.JobSubject",
            "cohort -> net.blueshell.api.jobs.web.JobSubjectResolver",
            "committee -> net.blueshell.api.shared.web.AdvancedController",
            "config -> net.blueshell.api.user.web.MemberProfileResponse",
            "config -> net.blueshell.api.platform.web.dto.error.ApiErrorDTO",
            "config -> net.blueshell.api.platform.web.dto.error.FieldValidationErrorDTO",
            "contribution -> net.blueshell.api.jobs.web.JobSubject",
            "contribution -> net.blueshell.api.jobs.web.JobSubjectResolver",
            "contribution -> net.blueshell.api.shared.web.BaseController",
            "event -> net.blueshell.api.survey.web.AnswerRequest",
            "event -> net.blueshell.api.survey.web.SurveyRequest",
            "event -> net.blueshell.api.survey.web.AnswerResponse",
            "event -> net.blueshell.api.survey.web.SurveyResponse",
            "event -> net.blueshell.api.survey.web.SurveyRequestMappingsKt",
            "event -> net.blueshell.api.survey.web.SurveyResponseMappingsKt",
            "event -> net.blueshell.api.user.web.UserSummaryResponse",
            "event -> net.blueshell.api.user.web.UserResponseMappingsKt",
            "event -> net.blueshell.api.jobs.web.JobSubject",
            "event -> net.blueshell.api.jobs.web.JobSubjectResolver",
            "event -> net.blueshell.api.shared.web.BaseController",
            "file -> net.blueshell.api.shared.web.BaseController",
            "sponsor -> net.blueshell.api.shared.web.BaseController",
            "telemetry -> net.blueshell.api.shared.web.BaseController",
            "user -> net.blueshell.api.shared.web.AdvancedController",
            "user -> net.blueshell.api.shared.web.BaseController",
        )
    }

    @Test
    fun `no module reaches into another module's web package`() {
        val offenders = measureReaches()
            .filterKeys { it !in PINNED }
            .flatMap { (reach, origins) -> origins.map { "$reach   from $it" } }
            .sorted()

        assertThat(offenders)
            .describedAs(
                "architecture ADR-003: a module's web package is internal. Publish what another " +
                    "module needs through the owning module's api surface, or add the reach to " +
                    "PINNED if it is being cleaned up separately",
            )
            .isEmpty()
    }

    @Test
    fun `no pinned reach outlives the code that made it`() {
        val measured = measureReaches().keys

        val stale = PINNED.filterNot { it in measured }.sorted()

        assertThat(stale)
            .describedAs(
                "these reaches are gone — drop them from PINNED so the ratchet cannot slip back",
            )
            .isEmpty()
    }

    /**
     * Every `<module> -> <type>` reach into another module's web package, mapped
     * to the classes that make it. Keyed on the type rather than on the reaching
     * class so the pinned list survives a rename on the consuming side.
     */
    private fun measureReaches(): Map<String, Set<String>> {
        val reaches = mutableMapOf<String, MutableSet<String>>()

        importedClasses.forEach { origin ->
            val originModule = ArchModules.moduleOf(origin) ?: return@forEach
            origin.directDependenciesFromSelf.forEach { dependency ->
                val target = dependency.targetClass
                val targetModule = ArchModules.moduleOf(target) ?: return@forEach
                if (targetModule == originModule) return@forEach
                if (!ArchModules.isWebPackage(target.packageName)) return@forEach
                reaches.getOrPut("$originModule -> ${target.fullName}") { mutableSetOf() }
                    .add(origin.fullName)
            }
        }

        return reaches
    }
}
