package net.blueshell.api.platform.logging

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.JobExecutor
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.jobs.domain.JobDispatcher
import net.blueshell.api.jobs.domain.JobHandler
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.sync.domain.ContactSyncService
import net.blueshell.api.testsupport.UserTestSupport
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/** Known details sent down every path that logs or records a failure, none of which may surface. */
@ExtendWith(OutputCaptureExtension::class)
@Import(LeakingJobConfig::class)
@TestPropertySource(properties = ["app.jobs.recovery.enabled=false"])
class PersonalDetailsStayOutOfLogsIT : UserTestSupport() {
    @Autowired
    private lateinit var dispatcher: JobDispatcher

    @Autowired
    private lateinit var executor: JobExecutor

    @Autowired
    private lateinit var leakingJob: LeakingJobHandler

    @Autowired
    private lateinit var emailSender: EmailSenderService

    @Autowired
    private lateinit var contactSync: ContactSyncService

    private val known = listOf(EMAIL, PHONE, IBAN)

    @Test
    fun `a refused request logs no detail it was sent`(output: CapturedOutput) {
        val member = createUserWithRole(Role.MEMBER)

        known.forEach { value ->
            mvc
                .perform(
                    post("/addresses")
                        .with(signedIn(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"userId":"$value"}"""),
                ).andExpect(status().isBadRequest)
        }

        assertThat(output.all).contains("HttpMessageNotReadableException").doesNotContain(known)
    }

    @Test
    fun `a failed job logs and records the constraint it broke, not the value`(output: CapturedOutput) {
        val holder = createUserWithRole(Role.MEMBER).apply { email = EMAIL }
        userRepository.saveAndFlush(holder.apply { phoneNumber = PHONE })

        val failed =
            listOf(Leak.EMAIL, Leak.PHONE, Leak.IBAN).map { leak ->
                leakingJob.leak = leak
                val execution = dispatcher.runAsync(LeakingJobHandler.JOB_TYPE, mapOf("leak" to leak.name), JobTrigger.BY_HAND)!!
                executor.execute(jobExecutions.findById(execution.id!!).orElseThrow())
                jobExecutions.findById(execution.id!!).orElseThrow()
            }

        val recorded = failed.joinToString("\n") { "${it.errorMessage}\n${it.errorReason}" }
        assertThat(recorded).contains("uk_users_email_deleted_at", "uk_users_phone_number_deleted_at").doesNotContain(known)
        assertThat(output.all).contains("uk_users_email_deleted_at").doesNotContain(known)
    }

    @Test
    fun `sending an email and syncing a contact log ids, not addresses`(output: CapturedOutput) {
        val member = createUserWithRole(Role.MEMBER)
        userRepository.saveAndFlush(member.apply { email = EMAIL }.apply { phoneNumber = PHONE })

        emailSender.send(EmailContent(EMAIL, "Leak Check", "Your mandate", "IBAN $IBAN"), "test.leak")
        contactSync.sync(member.id!!)

        assertThat(output.all).contains("Sent email id=").contains("for user ${member.id}").doesNotContain(known)
    }

    @Test
    fun `any line that still carries an email address or an IBAN is masked`(output: CapturedOutput) {
        LoggerFactory.getLogger(PersonalDetailsStayOutOfLogsIT::class.java).warn("A stray {} beside {}", EMAIL, IBAN)

        assertThat(output.all).contains("A stray [email] beside [iban]").doesNotContain(EMAIL, IBAN)
    }

    companion object {
        const val EMAIL = "leak.check@example.org"
        const val PHONE = "+31687654321"
        const val IBAN = "NL91ABNA0417164300"
    }
}

enum class Leak { EMAIL, PHONE, IBAN }

@TestConfiguration
class LeakingJobConfig {
    @Bean
    fun leakingJobHandler(users: UserRepository): LeakingJobHandler = LeakingJobHandler(users)
}

/** Fails on a unique email, a unique phone number or a message naming an IBAN, as [leak] says. */
class LeakingJobHandler(
    private val users: UserRepository,
) : JobHandler {
    override val jobType: String = JOB_TYPE
    override val payloadType: Class<*> = Map::class.java

    @Volatile
    var leak: Leak = Leak.EMAIL

    override fun handle(
        payload: String?,
        executionId: Long?,
        forced: Boolean,
    ): JobOutcome {
        if (leak == Leak.IBAN) throw IllegalStateException("The mandate on ${PersonalDetailsStayOutOfLogsIT.IBAN} was refused")
        val email = if (leak == Leak.EMAIL) PersonalDetailsStayOutOfLogsIT.EMAIL else "fresh.${System.nanoTime()}@test.com"
        users.saveAndFlush(
            User(
                username = "leak_${System.nanoTime()}",
                email = email,
                password = "h",
                initials = "L",
                firstName = "Leak",
                lastName = "Check",
            ).apply {
                phoneNumber =
                    if (leak ==
                        Leak.PHONE
                    ) {
                        PersonalDetailsStayOutOfLogsIT.PHONE
                    } else {
                        "06${System.nanoTime() % 100_000_000}"
                    }
            },
        )
        return JobOutcome.Done()
    }

    companion object {
        const val JOB_TYPE = "test.leaking"
    }
}
