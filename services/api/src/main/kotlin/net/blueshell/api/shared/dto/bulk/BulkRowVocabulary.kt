package net.blueshell.api.shared.dto.bulk

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Carries the bulk-action enums into the generated client.
 *
 * No endpoint returns this. Rows are decided in the browser, so the frontend needs the
 * values themselves rather than a response that happens to contain them — and springdoc
 * only emits schemas an endpoint reaches, so an enum nothing returns never arrives.
 * Registered in [net.blueshell.api.platform.config.OpenApiSchemasCustomizer].
 */
@Schema(
    name = "BulkRowVocabulary",
    description = "Contract-only holder that publishes the bulk-action enums. Not returned by any endpoint.",
)
data class BulkRowVocabulary(
    val disposition: BulkRowDisposition,
    val reason: BulkRowReason,
    val feeType: BulkFeeType,
    val feeCycleGroup: FeeCycleGroup,
)
