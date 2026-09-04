package net.blueshell.api.jobs.web

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
 * Classification of a payload field, so the admin UI picks an input without parsing the raw
 * [JobPayloadFieldDTO.type].
 */
@Schema(name = "JobPayloadFieldKind", enumAsRef = true)
enum class JobPayloadFieldKind {
    /** Numeric, boolean or string: a plain text or number control. */
    PRIMITIVE,

    /** A Kotlin or Java enum; [JobPayloadFieldDTO.enumValues] carries the options. */
    ENUM,

    /** Anything else — nested DTO, collection — which falls back to a free-text JSON input. */
    OBJECT,
}

/** Request to manually enqueue a job from the admin UI. */
@Schema(name = "EnqueueJobRequest")
data class EnqueueJobRequest(
    @field:NotBlank
    val jobType: String,
    @field:Schema(description = "Job payload fields keyed by name; shape depends on the job type")
    val payload: Map<String, Any?>? = null,
)
