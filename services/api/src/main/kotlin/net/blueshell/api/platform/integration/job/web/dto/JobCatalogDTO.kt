package net.blueshell.api.platform.integration.job.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

/** A triggerable job type and the shape of the payload it expects. */
@Schema(name = "JobTypeDescriptor")
data class JobTypeDescriptorDTO(
    val type: String,
    val payloadFields: List<JobPayloadFieldDTO>,
)

/** One field of a job payload, reflected from the payload data class constructor. */
@Schema(name = "JobPayloadField")
data class JobPayloadFieldDTO(
    val name: String,
    val type: String,
    val required: Boolean,
)

/** Request to manually enqueue a job from the admin UI. */
@Schema(name = "EnqueueJobRequest")
data class EnqueueJobRequest(
    @field:NotBlank
    val jobType: String,
    @field:Schema(description = "Job payload fields keyed by name; shape depends on the job type")
    val payload: Map<String, Any?>? = null,
)
