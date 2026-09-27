package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.shared.model.DESCRIPTION_MAX
import java.time.Instant

@Schema(name = "CommitteeSummaryResponse")
data class CommitteeSummaryResponse(
    var id: Long,
    @field:NotBlank
    @field:Size(max = 255)
    var name: String,
    @field:NotBlank
    @field:Size(max = DESCRIPTION_MAX)
    var description: String,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
) : CommitteeResponse
