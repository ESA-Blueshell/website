package net.blueshell.api.platform.integration.job.web.service

import net.blueshell.api.platform.integration.job.persistence.JobExecution
import net.blueshell.api.platform.integration.queue.JobDispatcher
import net.blueshell.api.platform.integration.queue.JobHandlerRegistry
import net.blueshell.api.shared.job.ContactJobs
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import tools.jackson.databind.ObjectMapper

class JobCatalogServiceTest {

    private val registry: JobHandlerRegistry = mock()
    private val dispatcher: JobDispatcher = mock()
    private val objectMapper: ObjectMapper = mock()
    private val service = JobCatalogService(registry, dispatcher, objectMapper)

    @Test
    fun `describe reflects payload fields from the data class constructor`() {
        whenever(registry.jobTypes()).thenReturn(setOf(ContactJobs.SyncContact.type))
        whenever(registry.payloadType(ContactJobs.SyncContact.type))
            .thenReturn(ContactJobs.SyncContactPayload::class.java)

        val descriptors = service.describe()

        assertThat(descriptors).hasSize(1)
        val descriptor = descriptors.single()
        assertThat(descriptor.type).isEqualTo(ContactJobs.SyncContact.type)
        assertThat(descriptor.payloadFields).hasSize(1)
        val field = descriptor.payloadFields.single()
        assertThat(field.name).isEqualTo("userId")
        assertThat(field.type).isEqualTo("Long")
        assertThat(field.required).isTrue()
    }

    @Test
    fun `enqueue deserializes the payload and dispatches without dedup`() {
        val rawPayload = mapOf<String, Any?>("userId" to 7)
        val payload = ContactJobs.SyncContactPayload(userId = 7)
        val execution = JobExecution(jobType = ContactJobs.SyncContact.type).apply { id = 99L }
        whenever(registry.payloadType(ContactJobs.SyncContact.type))
            .thenReturn(ContactJobs.SyncContactPayload::class.java)
        whenever(objectMapper.convertValue(eq(rawPayload), eq(ContactJobs.SyncContactPayload::class.java)))
            .thenReturn(payload)
        whenever(dispatcher.enqueue(eq(ContactJobs.SyncContact.type), eq(payload), isNull(), isNull()))
            .thenReturn(execution)

        val result = service.enqueue(ContactJobs.SyncContact.type, rawPayload)

        assertThat(result.id).isEqualTo(99L)
    }

    @Test
    fun `enqueue rejects an unknown job type`() {
        whenever(registry.payloadType("nope.unknown")).thenReturn(null)

        assertThatThrownBy { service.enqueue("nope.unknown", null) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Unknown job type")
    }

    @Test
    fun `enqueue rejects a malformed payload`() {
        whenever(registry.payloadType(ContactJobs.SyncContact.type))
            .thenReturn(ContactJobs.SyncContactPayload::class.java)
        whenever(objectMapper.convertValue(any<Map<String, Any?>>(), eq(ContactJobs.SyncContactPayload::class.java)))
            .thenThrow(IllegalArgumentException("bad field"))

        assertThatThrownBy { service.enqueue(ContactJobs.SyncContact.type, mapOf("userId" to "x")) }
            .isInstanceOf(ResponseStatusException::class.java)
            .hasMessageContaining("Invalid payload")
    }
}
