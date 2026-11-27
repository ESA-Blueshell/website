package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.jobs.api.EMAIL_RETRY_SCHEDULE
import net.blueshell.api.jobs.api.RetrySchedule
import net.blueshell.api.jobs.domain.EmailJobWorker
import net.blueshell.api.jobs.domain.JobDispatcher
import net.blueshell.api.jobs.persistence.JobExecution
import net.blueshell.api.shared.email.EmailContent
import net.blueshell.api.shared.enums.JobExecutionStatus
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.testsupport.ServiceTestSupport
import net.blueshell.api.testsupport.SmtpProxy
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.UUID

// The batch is drained by hand; a poller would race the assertions on status and backoff.
@TestPropertySource(properties = ["app.jobs.recovery.enabled=false"])
@Import(SmtpEmailBatchConfig::class)
class SmtpEmailBatchIT : ServiceTestSupport() {
    @Autowired
    private lateinit var dispatcher: JobDispatcher

    @Autowired
    private lateinit var emailJobWorker: EmailJobWorker

    @Autowired
    private lateinit var mailSender: JavaMailSender

    @Autowired
    private lateinit var mapper: ObjectMapper

    private val http = HttpClient.newHttpClient()

    private val mailpitApi: String get() = "http://$PROXY_HOST:${mailpit.getMappedPort(API_PORT)}"

    private val sender: JavaMailSenderImpl get() = mailSender as JavaMailSenderImpl

    @BeforeEach
    fun resetMail() {
        clearMessages()
        proxy.reset()
        sender.apply {
            host = PROXY_HOST
            // A blank username still counts as one to Jakarta Mail; null keeps it from offering AUTH.
            username = null
        }
    }

    @Test
    fun `two hundred jobs arrive exactly once over at most five connections`() {
        val subjects = enqueue(JOB_COUNT)

        emailJobWorker.drain()

        val mailbox = mailbox()
        assertThat(mailbox.messagesCount).isEqualTo(JOB_COUNT)
        assertThat(mailbox.total).isEqualTo(JOB_COUNT)
        assertThat(mailbox.subjects).containsExactlyInAnyOrderElementsOf(subjects)
        assertThat(proxy.acceptedConnections()).isLessThanOrEqualTo(MAX_CONNECTIONS)
        assertThat(jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE))
            .hasSize(JOB_COUNT)
            .allSatisfy { assertThat(it.status).isEqualTo(JobExecutionStatus.SUCCESS) }
    }

    @Test
    fun `an unresolvable host leaves jobs queued for thirty seconds and they deliver once it returns`() {
        val subjects = enqueue(SMALL_BATCH)
        val originalHost = sender.host
        val rows: List<JobExecution>
        try {
            sender.host = UNRESOLVABLE_HOST
            emailJobWorker.drain()
            rows = jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE)
        } finally {
            sender.host = originalHost
        }

        val earliest = Instant.now().plusSeconds(20)
        val latest = Instant.now().plusSeconds(45)
        assertThat(rows).hasSize(subjects.size)
        assertThat(rows).allSatisfy { row ->
            assertThat(row.status).isEqualTo(JobExecutionStatus.QUEUED)
            assertThat(row.nextAttemptAt).isBetween(earliest, latest)
            assertThat(row.errorReason).contains("UnknownHostException")
        }
        assertThat(mailbox().total).isZero()

        makeDue()
        emailJobWorker.drain()

        assertThat(mailbox().subjects).containsExactlyInAnyOrderElementsOf(subjects)
        assertThat(jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE))
            .allSatisfy { assertThat(it.status).isEqualTo(JobExecutionStatus.SUCCESS) }
    }

    @Test
    fun `a one connection cap leaves jobs queued rather than failed`() {
        val subjects = enqueue(JOB_COUNT)
        proxy.limitPerIp(1)

        emailJobWorker.drain()

        assertThat(jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE))
            .hasSize(subjects.size)
            .allSatisfy { assertThat(it.status).isIn(JobExecutionStatus.QUEUED, JobExecutionStatus.SUCCESS) }

        proxy.limitPerIp(Int.MAX_VALUE)
        makeDue()
        emailJobWorker.drain()

        assertThat(mailbox().subjects).containsExactlyInAnyOrderElementsOf(subjects)
        assertThat(jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE))
            .allSatisfy { assertThat(it.status).isEqualTo(JobExecutionStatus.SUCCESS) }
    }

    @Test
    fun `dropping connections after twenty five delivered messages loses no job`() {
        val subjects = enqueue(JOB_COUNT)
        proxy.dropAfterMessages(DROP_AFTER)

        emailJobWorker.drain()
        proxy.dropAll()

        assertThat(jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE))
            .allSatisfy { assertThat(it.status).isIn(JobExecutionStatus.QUEUED, JobExecutionStatus.SUCCESS) }

        makeDue()
        emailJobWorker.drain()

        assertThat(mailbox().subjects).containsExactlyInAnyOrderElementsOf(subjects)
        assertThat(jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE))
            .allSatisfy { assertThat(it.status).isEqualTo(JobExecutionStatus.SUCCESS) }
    }

    private fun enqueue(count: Int): List<String> {
        val run = UUID.randomUUID().toString().take(8)
        return (1..count).map { index ->
            val subject = "smtp-batch-$run-$index"
            dispatcher.runAsync(
                SmtpBatchEmailJob.Definition,
                SmtpBatchEmailJob.Payload(subject),
                JobTrigger.BY_HAND,
                Actor.system(),
            )
            subject
        }
    }

    /** Only rows a failed attempt postponed; a successful row must not be sent a second time. */
    private fun makeDue() {
        transactionTemplate.execute {
            jobExecutions.findByJobType(SmtpBatchEmailJob.JOB_TYPE).forEach { row ->
                row.nextAttemptAt?.let { row.nextAttemptAt = Instant.now().minusSeconds(1) }
            }
        }
    }

    private fun clearMessages() {
        val request = HttpRequest.newBuilder(URI.create("$mailpitApi/api/v1/messages")).DELETE().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        assertThat(response.statusCode()).isBetween(200, 299)
    }

    private fun mailbox(): Mailbox {
        val subjects = mutableListOf<String>()
        var start = 0
        var messagesCount = 0
        var total = 0
        do {
            val uri = URI.create("$mailpitApi/api/v1/messages?start=$start&limit=$MESSAGE_PAGE")
            val response = http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
            assertThat(response.statusCode()).isBetween(200, 299)
            val page: JsonNode = mapper.readTree(response.body())
            val messages = page.path("messages")
            messagesCount += page.path("messages_count").asInt().takeIf { it > 0 } ?: messages.size()
            total = page.path("total").asInt()
            messages.forEach { subjects += it.path("Subject").asString().ifBlank { it.path("subject").asString() } }
            start += messages.size()
        } while (messages.size() > 0 && subjects.size < total)
        return Mailbox(messagesCount, total, subjects)
    }

    private data class Mailbox(
        val messagesCount: Int,
        val total: Int,
        val subjects: List<String>,
    )

    companion object {
        private const val SMTP_PORT = 1025
        private const val API_PORT = 8025
        private const val PROXY_HOST = "127.0.0.1"
        private const val UNRESOLVABLE_HOST = "smtp-no-such-host.invalid"
        private const val JOB_COUNT = 200
        private const val SMALL_BATCH = 12
        private const val MAX_CONNECTIONS = 5
        private const val DROP_AFTER = 25
        private const val MESSAGE_PAGE = 1000

        private val mailpit =
            GenericContainer(DockerImageName.parse("axllent/mailpit:v1.21.4"))
                .withExposedPorts(SMTP_PORT, API_PORT)
                .also { it.start() }

        private val proxy = SmtpProxy(PROXY_HOST, mailpit.getMappedPort(SMTP_PORT))

        @JvmStatic
        @DynamicPropertySource
        fun mailProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.mail.host") { PROXY_HOST }
            registry.add("spring.mail.port") { proxy.port.toString() }
            registry.add("spring.mail.username") { "" }
            registry.add("spring.mail.password") { "" }
            registry.add("spring.mail.properties.mail.smtp.auth") { "false" }
            registry.add("spring.mail.properties.mail.smtp.starttls.enable") { "false" }
            registry.add("spring.mail.properties.mail.smtp.starttls.required") { "false" }
        }

        @JvmStatic
        @AfterAll
        fun stopMailpit() {
            proxy.close()
            mailpit.stop()
        }
    }
}

@TestConfiguration(proxyBeanMethods = false)
class SmtpEmailBatchConfig {
    // A relay host swaps InMemoryEmailClient for SmtpEmailClient, but the test profile's
    // TestSupportController still needs the in-memory one; the real client stays the one sends use.
    @Bean
    fun inMemoryEmailClient(): InMemoryEmailClient = InMemoryEmailClient()

    @Bean
    @Primary
    fun sendingEmailClient(smtp: SmtpEmailClient): EmailTransportClient = smtp

    @Bean
    fun smtpBatchEmailJob(
        objectMapper: ObjectMapper,
        emails: EmailSenderService,
    ): SmtpBatchEmailJob = SmtpBatchEmailJob(objectMapper, emails)
}

/** Sends one message per job so each state transition in a batch has its own subject to assert on. */
open class SmtpBatchEmailJob(
    objectMapper: ObjectMapper,
    private val emails: EmailSenderService,
) : AbstractJsonJobHandler<SmtpBatchEmailJob.Payload>(objectMapper, SmtpBatchEmailJob.Definition) {
    data class Payload(
        val subject: String,
    )

    object Definition : JobDefinition<Payload> {
        override val type: String get() = JOB_TYPE

        override val payloadType: Class<Payload> get() = Payload::class.java
    }

    override val retrySchedule: RetrySchedule get() = EMAIL_RETRY_SCHEDULE

    override fun handlePayload(payload: Payload) {
        emails.send(
            EmailContent(
                recipientEmail = "smtp-batch@example.com",
                recipientName = "SMTP Batch",
                subject = payload.subject,
                markdownContent = "SMTP batch message ${payload.subject}",
            ),
            JOB_TYPE,
            currentExecutionId,
        )
    }

    companion object {
        const val JOB_TYPE = "email.smtp.batch.test"
    }
}
