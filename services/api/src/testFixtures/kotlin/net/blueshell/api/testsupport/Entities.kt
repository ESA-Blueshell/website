package net.blueshell.api.testsupport

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.committee.persistence.CommitteeMember
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.enums.FileType
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.user.persistence.User
import java.time.Instant

/**
 * Real entities for a unit test, built without a database: a test names the fields it reads and
 * takes a default for the rest. A mocked entity answers only what was stubbed, so a rule that
 * reads one more field passes on a null the entity could never hold.
 */
object Entities {
    private val START: Instant = Instant.parse("2026-10-01T18:00:00Z")

    fun user(
        id: Long? = null,
        username: String = "user${id ?: 0}",
        email: String = "$username@example.com",
        firstName: String = "User",
        lastName: String = "${id ?: 0}",
        roles: Set<Role> = setOf(Role.GUEST),
        enabled: Boolean = true,
    ): User =
        User(
            username = username,
            email = email,
            password = "hashed",
            initials = firstName.take(1) + ".",
            firstName = firstName,
            lastName = lastName,
            enabled = enabled,
            roles = roles.toMutableSet(),
        ).also { it.id = id }

    /** A committee seating an account for each of [memberIds]. */
    fun committee(
        id: Long? = null,
        name: String = "Committee ${id ?: 0}",
        description: String = "A committee",
        memberIds: List<Long> = emptyList(),
    ): Committee =
        Committee(name = name, description = description).also { committee ->
            committee.id = id
            committee.replaceMembers(memberIds.map { CommitteeMember(committee = committee, user = user(id = it)) })
        }

    fun event(
        id: Long? = null,
        committee: Committee? = null,
        title: String = "Event ${id ?: 0}",
        startTime: Instant = START,
        endTime: Instant = startTime.plusSeconds(3600),
        approved: Boolean = false,
        membersOnly: Boolean = false,
        signUp: Boolean = false,
    ): Event =
        Event(
            committee = committee,
            title = title,
            startTime = startTime,
            endTime = endTime,
            approved = approved,
            membersOnly = membersOnly,
            signUp = signUp,
        ).also { it.id = id }

    fun signUp(
        id: Long? = null,
        event: Event = event(),
        userId: Long? = null,
        guest: Guest? = null,
    ): EventSignUp = EventSignUp(event = event, userId = userId, guest = guest).also { it.id = id }

    fun question(
        id: Long? = null,
        survey: Survey = Survey(),
        type: QuestionType = QuestionType.OPEN,
        label: String = "Question ${id ?: 0}",
    ): Question = Question(idx = 0, survey = survey, type = type, label = label).also { it.id = id }

    fun file(
        id: Long? = null,
        uploader: User = user(),
        type: FileType = FileType.EVENT_BANNER,
        name: String = "file-${id ?: 0}.webp",
        path: String = "${type.directory}/$name",
    ): File =
        File(
            name = name,
            path = path,
            uploader = uploader,
            mediaType = "image/webp",
            type = type,
        ).also { it.id = id }

    fun banner(
        event: Event,
        file: File = file(),
    ): EventBanner = EventBanner(event = event, file = file)
}
