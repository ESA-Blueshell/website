package net.blueshell.api.domain.user.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.StudyLevel
import net.blueshell.api.shared.enums.StudyStatus

@Schema(name = "UserStudyRequest")
data class UserStudyRequest(
    var level: StudyLevel? = null,
    var programName: String? = null,
    var status: StudyStatus? = null,
    var startYear: Int? = null,
    var graduationYear: Int? = null
)
