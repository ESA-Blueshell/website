package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(name = "UpsertMemberProfileRequest")
data class UpsertMemberProfileRequest(
    @Schema(description = "Required of a member; anybody else may leave it out")
    var dateOfBirth: LocalDate? = null,
    var studentNumber: String? = null,
    var gender: String? = null,
    @Schema(description = "Required of a member; anybody else may leave it out")
    var nationality: String? = null,
    var bhv: Boolean,
    var ehbo: Boolean,
    @Schema(description = "Whether this member's real name may appear in a roster")
    var nameOnRosters: Boolean = false,
    var version: Long? = null,
)
