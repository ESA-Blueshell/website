package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.sponsor.application.SponsorService
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.domain.telemetry.application.TelemetryService
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.security.core.Authentication

class CompositePermissionEvaluatorTest {

    private val authentication = mock<Authentication>()
    private val telemetry = Telemetry(platform = net.blueshell.api.shared.enums.PlatformType.TWITTER, url = "https://example.com")

    @Nested
    inner class HasPermissionByEntity {

        @Test
        fun `delegates to matching evaluator by target class`() {
            val telemetryEvaluator = RecordingTelemetryEvaluator().apply { entityResult = true }
            val sponsorEvaluator = RecordingSponsorEvaluator().apply { entityResult = false }
            val evaluator = CompositePermissionEvaluator(mutableListOf(sponsorEvaluator, telemetryEvaluator))

            val allowed = evaluator.hasPermission(authentication, telemetry, "read")

            assertThat(allowed).isTrue()
            assertThat(telemetryEvaluator.entityCalls).isEqualTo(1)
            assertThat(sponsorEvaluator.entityCalls).isEqualTo(0)
        }

        @Test
        fun `returns false when no evaluator supports target class`() {
            val evaluator = CompositePermissionEvaluator(mutableListOf(RecordingTelemetryEvaluator()))

            val allowed = evaluator.hasPermission(authentication, Sponsor(name = "abc", description = "cba"), "read")

            assertThat(allowed).isFalse()
        }
    }

    @Nested
    inner class HasPermissionById {

        @Test
        fun `delegates to hasPermissionId for matching simple type name`() {
            val telemetryEvaluator = RecordingTelemetryEvaluator().apply { idResult = true }
            val evaluator = CompositePermissionEvaluator(mutableListOf(telemetryEvaluator))

            val allowed = evaluator.hasPermission(authentication, 42L, "Telemetry", "read")

            assertThat(allowed).isTrue()
            assertThat(telemetryEvaluator.idCalls).isEqualTo(1)
            assertThat(telemetryEvaluator.entityCalls).isEqualTo(0)
        }

        @Test
        fun `delegates to hasPermissionId for matching fully qualified type name`() {
            val telemetryEvaluator = RecordingTelemetryEvaluator().apply { idResult = true }
            val evaluator = CompositePermissionEvaluator(mutableListOf(telemetryEvaluator))

            val allowed = evaluator.hasPermission(authentication, 42L, Telemetry::class.java.name, "read")

            assertThat(allowed).isTrue()
            assertThat(telemetryEvaluator.idCalls).isEqualTo(1)
        }

        @Test
        fun `returns false when no evaluator supports target type`() {
            val evaluator = CompositePermissionEvaluator(mutableListOf(RecordingTelemetryEvaluator()))

            val allowed = evaluator.hasPermission(authentication, 1L, "UnknownType", "read")

            assertThat(allowed).isFalse()
        }
    }

    private class RecordingTelemetryEvaluator(
        service: TelemetryService = mock()
    ) : BasePermissionEvaluator<Telemetry, Long, TelemetryService>(service) {
        var entityResult: Boolean = false
        var idResult: Boolean = false
        var entityCalls: Int = 0
        var idCalls: Int = 0

        override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
            entityCalls += 1
            return entityResult
        }

        override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
            idCalls += 1
            return idResult
        }
    }

    private class RecordingSponsorEvaluator(
        service: SponsorService = mock()
    ) : BasePermissionEvaluator<Sponsor, Long, SponsorService>(service) {
        var entityResult: Boolean = false
        var idResult: Boolean = false
        var entityCalls: Int = 0
        var idCalls: Int = 0

        override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
            entityCalls += 1
            return entityResult
        }

        override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
            idCalls += 1
            return idResult
        }
    }
}
