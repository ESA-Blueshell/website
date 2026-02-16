package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.application.validation.UniqueUserCommand
import net.blueshell.api.domain.user.application.validation.UserUniquenessCandidate
import net.blueshell.api.domain.user.persistence.StudyProgram
import net.blueshell.api.domain.user.persistence.UserStudy
import net.blueshell.api.shared.enums.StudyLevel
import net.blueshell.api.shared.enums.StudyStatus
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.Role
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.sql.Date

data class UserStudyData(
    val level: StudyLevel,
    val programName: String,
    val status: StudyStatus,
    val startYear: Int? = null,
    val graduationYear: Int? = null
)

@UniqueUserCommand
data class CreateUserCommand(
    val isBoard: Boolean,
    val photoConsent: Boolean,
    override val username: String?,
    override val email: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val password: String?,
    override val discord: String?,
    override val phoneNumber: String?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null
}

@UniqueUserCommand
data class CreateGuestUserCommand(
    override val username: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    val password: String?,
    override val email: String?,
    override val discord: String?,
    override val phoneNumber: String?
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long? = null
}

@UniqueUserCommand
data class UpdateGuestUserCommand(
    val id: Long,
    override val discord: String?,
    override val phoneNumber: String?,
    val newsletter: Boolean,
    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long = id
    override val username: String? = null
    override val email: String? = null
}

@UniqueUserCommand
data class UpdateUserCommand(
    val id: Long,
    val isBoard: Boolean,
    override val username: String?,
    override val email: String?,
    val initials: String?,
    val firstName: String?,
    val prefix: String?,
    val lastName: String?,
    val newsletter: Boolean,
    override val discord: String?,
    override val phoneNumber: String?,
    @field:NotNull(message = "Version is required for optimistic locking")
    val version: Long
) : Command<User>, UserUniquenessCandidate {
    override val subjectId: Long = id
}

data class FindUsersCommand(
    val filter: UserQuery,
    val pageable: Pageable
) : Command<Page<User>>

data class FindUserByIdCommand(
    val userId: Long
) : Command<User>

data class DeleteUserByIdCommand(
    val userId: Long
) : Command<Unit>

data class ToggleUserRoleCommand(
    val userId: Long,
    val role: Role
) : Command<User>
