package net.blueshell.api.shared.credentials

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.context.annotation.ConditionContext
import org.springframework.core.type.AnnotationMetadata
import org.springframework.mock.env.MockEnvironment

class CredentialsTest {
    @WhenCredentialsSet("vendor.key", "vendor.secret")
    private class Real

    @WhenCredentialsMissing("vendor.key", "vendor.secret")
    private class StandIn

    private fun context(vararg set: Pair<String, String>): ConditionContext {
        val environment = MockEnvironment().also { env -> set.forEach { (key, value) -> env.setProperty(key, value) } }
        return mock { on { this.environment } doReturn environment }
    }

    private fun real(context: ConditionContext) = CredentialsSet().matches(context, AnnotationMetadata.introspect(Real::class.java))

    private fun standIn(context: ConditionContext) =
        CredentialsMissing().matches(context, AnnotationMetadata.introspect(StandIn::class.java))

    @Test
    fun `the real client runs where every credential is set, and its stand-in does not`() {
        val context = context("vendor.key" to "k", "vendor.secret" to "s")

        assertThat(real(context)).isTrue()
        assertThat(standIn(context)).isFalse()
    }

    @Test
    fun `a blank or absent credential brings the stand-in instead`() {
        listOf(context(), context("vendor.key" to "k"), context("vendor.key" to "k", "vendor.secret" to " ")).forEach {
            assertThat(real(it)).isFalse()
            assertThat(standIn(it)).isTrue()
        }
    }
}
