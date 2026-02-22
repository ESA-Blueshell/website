package net.blueshell.clients.brevo.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Request body for previewing a template.
 */
data class TemplatePreviewRequestBody(
    @field:JsonProperty("templateId")
    var templateId: kotlin.Long,

    @field:JsonProperty("email")
    var email: kotlin.String? = null,

    @field:JsonProperty("params")
    var params: kotlin.collections.MutableMap<kotlin.String, kotlin.Any?>? = null
)
