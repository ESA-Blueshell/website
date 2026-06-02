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
    val kind: JobPayloadFieldKind,
    val required: Boolean,
    /** Populated when [kind] is [JobPayloadFieldKind.ENUM]; the literal enum constant names in declaration order. */
    val enumValues: List<String>? = null,
)

/**
 * Coarse classification of a payload field so the admin UI can pick
 * the right input component without parsing the raw
 * [JobPayloadFieldDTO.type]:
 *  - [PRIMITIVE]: numeric / boolean / string, render a plain text or
 *    number control.
 *  - [ENUM]: a Kotlin/Java enum; [JobPayloadFieldDTO.enumValues]
 *    carries the options.
 *  - [OBJECT]: anything else (nested DTO, collection, ...) — the UI
 *    currently falls back to a free-text JSON input.
 */
@Schema(name = "JobPayloadFieldKind", enumAsRef = true)
enum class JobPayloadFieldKind { PRIMITIVE, ENUM, OBJECT }

/** Request to manually enqueue a job from the admin UI. */
@Schema(name = "EnqueueJobRequest")
data class EnqueueJobRequest(
    @field:NotBlank
    val jobType: String,
    @field:Schema(description = "Job payload fields keyed by name; shape depends on the job type")
    val payload: Map<String, Any?>? = null,
)
