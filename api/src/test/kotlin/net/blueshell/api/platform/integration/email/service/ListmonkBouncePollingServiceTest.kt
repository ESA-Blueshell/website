package net.blueshell.api.platform.integration.email.service

import net.blueshell.api.platform.integration.email.application.service.EmailService
import net.blueshell.api.platform.integration.email.application.service.EmailSuppressionService
import net.blueshell.api.platform.integration.email.persistence.Email
import net.blueshell.clients.listmonk.api.BouncesApi
import net.blueshell.clients.listmonk.model.BounceRecord
import net.blueshell.clients.listmonk.model.GetBounces200Response
import net.blueshell.clients.listmonk.model.GetBounces200ResponseData
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Unit tests for [ListmonkBouncePollingService].
 *
 * No Spring context — instantiate directly with mocks.
 * The service's `lastPollTime` is initialised to `Instant.now().minus(1h)`, so:
 *   - "new" bounces have `createdAt = Instant.now().minus(30m)` (within the window)
 *   - "old" bounces have `createdAt = Instant.now().minus(2h)` (before the window)
 */
class ListmonkBouncePollingServiceTest {

    private val bouncesApi: BouncesApi = mock()
    private val emailService: EmailService = mock()
    private val suppressionService: EmailSuppressionService = mock()

    private val service = ListmonkBouncePollingService(bouncesApi, emailService, suppressionService)

    private val recentEmail = "user@example.com"

    private fun recentBounceRecord(type: String, email: String = recentEmail): BounceRecord {
        val createdAt = Instant.now().minus(30, ChronoUnit.MINUTES)
        return BounceRecord()
            .type(type)
            .email(email)
            .createdAt(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
    }

    private fun oldBounceRecord(type: String = "hard"): BounceRecord {
        val createdAt = Instant.now().minus(2, ChronoUnit.HOURS)
        return BounceRecord()
            .type(type)
            .email(recentEmail)
            .createdAt(OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
    }

    private fun stubBounces(vararg records: BounceRecord) {
        val data = GetBounces200ResponseData().results(records.toList())
        val response = GetBounces200Response().data(data)
        whenever(bouncesApi.getBounces(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(response)
    }

    @BeforeEach
    fun setUp() {
        // Default: no outbox entry found
        whenever(emailService.findRecentByRecipientEmail(any(), any())).thenReturn(null)
    }

    @Test
    fun `hard bounce calls suppressHardBounce`() {
        stubBounces(recentBounceRecord("hard"))

        service.pollBounces()

        verify(suppressionService).suppressHardBounce(recentEmail)
    }

    @Test
    fun `soft bounce calls recordSoftBounce`() {
        stubBounces(recentBounceRecord("soft"))

        service.pollBounces()

        verify(suppressionService).recordSoftBounce(recentEmail)
    }

    @Test
    fun `complaint bounce calls suppressComplaint`() {
        stubBounces(recentBounceRecord("complaint"))

        service.pollBounces()

        verify(suppressionService).suppressComplaint(recentEmail)
    }

    @Test
    fun `unknown bounce type falls through to suppressHardBounce`() {
        stubBounces(recentBounceRecord("unknown"))

        service.pollBounces()

        verify(suppressionService).suppressHardBounce(recentEmail)
    }

    @Test
    fun `bounce older than lastPollTime is skipped`() {
        stubBounces(oldBounceRecord("hard"))

        service.pollBounces()

        verify(suppressionService, never()).suppressHardBounce(any())
        verify(suppressionService, never()).recordSoftBounce(any())
        verify(suppressionService, never()).suppressComplaint(any())
    }

    @Test
    fun `matched outbox entry is marked bounced`() {
        val outboxEntry: Email = mock()
        whenever(emailService.findRecentByRecipientEmail(eq(recentEmail), any())).thenReturn(outboxEntry)
        stubBounces(recentBounceRecord("hard"))

        service.pollBounces()

        verify(emailService).markBounced(eq(outboxEntry), any())
    }

    @Test
    fun `no outbox entry found - markBounced not called`() {
        whenever(emailService.findRecentByRecipientEmail(any(), any())).thenReturn(null)
        stubBounces(recentBounceRecord("hard"))

        service.pollBounces()

        verify(emailService, never()).markBounced(any(), any())
    }

    @Test
    fun `api exception is caught and does not propagate`() {
        doThrow(RuntimeException("Listmonk unavailable"))
            .whenever(bouncesApi).getBounces(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())

        // Should not throw
        service.pollBounces()

        verify(suppressionService, never()).suppressHardBounce(any())
    }
}
