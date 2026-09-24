package net.blueshell.api.committee.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.blueshell.api.file.api.Image
import java.time.Instant

@Schema(name = "CommitteeSummaryResponse")
data class CommitteeSummaryResponse(
    var id: Long,
    @field:NotBlank
    @field:Size(max = 255)
    var name: String,
    @field:NotBlank
    @field:Size(max = 4095)
    var description: String,
    @field:Schema(description = "The address the committee's page answers to")
    var slug: String,
    @field:Schema(description = "Whether the committee is shown among the committees to join")
    var listed: Boolean,
    @field:Schema(description = "Whether the committee no longer runs")
    var archived: Boolean,
    var banner: Image?,
    @field:Schema(description = "The codes of the games the committee organises events for")
    var gameCodes: List<String>,
    var version: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
) : CommitteeResponse
