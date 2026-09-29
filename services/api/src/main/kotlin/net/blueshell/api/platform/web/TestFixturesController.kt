package net.blueshell.api.platform.web

import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import net.blueshell.api.committee.api.CommitteePage
import net.blueshell.api.committee.api.CommitteeService
import net.blueshell.api.committee.domain.CommitteeMemberData
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.domain.ContributionUseCases
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.user.api.UserService
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.time.LocalDate

/**
 * Creates what a system test needs through the services that own it, so a column rename breaks
 * the api's build rather than a system shard at run time. Test profile only, like
 * [TestSupportController]; each write answers the new row's id.
 */
@RestController
@RequestMapping("/test-support")
@Profile("test")
@Hidden
@Tag(name = "Test Support")
class TestFixturesController(
    private val committees: CommitteeService,
    private val periods: ContributionPeriodService,
    private val contributions: ContributionUseCases,
    private val events: EventService,
    private val users: UserService,
) {
    data class CommitteeFixture(
        val name: String,
        val description: String,
    )

    data class CommitteeSeatFixture(
        val username: String,
        val role: String?,
    )

    data class ContributionFixture(
        val username: String,
    )

    data class PeriodFixture(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val halfYearCutoffDate: LocalDate,
        val halfYearFee: Double = 0.0,
        val fullYearFee: Double = 0.0,
        val alumniFee: Double = 0.0,
    )

    data class EventFixture(
        val committeeId: Long? = null,
        val title: String,
        val startTime: Instant,
        val endTime: Instant,
        val description: String? = null,
        val location: String? = null,
        val approved: Boolean = false,
        val signUp: Boolean = false,
        val membersOnly: Boolean = false,
        val signUpLimit: Int? = null,
    )

    @PostMapping("/committees")
    @PermitAll
    fun committee(
        @RequestBody fixture: CommitteeFixture,
    ): Long = requireNotNull(committees.createWithMembers(fixture.name, fixture.description, emptyList()).id)

    /** Seats the user on the committee, keeping everybody already on it and the rest of its page. */
    @PostMapping("/committees/{id}/members")
    @PermitAll
    fun seat(
        @PathVariable id: Long,
        @RequestBody fixture: CommitteeSeatFixture,
    ) {
        val committee = committees.findById(id)
        val seated = committee.members.map { CommitteeMemberData(it.userId, it.role) }
        val userId = requireNotNull(users.findByUsername(fixture.username).id)
        committees.updateWithMembers(
            id,
            committee.name,
            committee.description,
            seated + CommitteeMemberData(userId, fixture.role),
            version = null,
            page = CommitteePage(address = committee.slug),
        )
    }

    /** The period with these dates where one exists, since shards reuse the same dates. */
    @PostMapping("/contribution-periods")
    @PermitAll
    fun period(
        @RequestBody fixture: PeriodFixture,
    ): Long {
        val existing = periods.findAll().firstOrNull { it.startDate == fixture.startDate && it.endDate == fixture.endDate }
        val period =
            existing ?: periods.create(
                ContributionPeriod(
                    startDate = fixture.startDate,
                    endDate = fixture.endDate,
                    halfYearCutoffDate = fixture.halfYearCutoffDate,
                    halfYearFee = fixture.halfYearFee,
                    fullYearFee = fixture.fullYearFee,
                    alumniFee = fixture.alumniFee,
                ),
            )
        return requireNotNull(period.id)
    }

    @PostMapping("/contribution-periods/{id}/contributions")
    @PermitAll
    fun contribution(
        @PathVariable id: Long,
        @RequestBody fixture: ContributionFixture,
    ) {
        contributions.create(requireNotNull(users.findByUsername(fixture.username).id), id)
    }

    @PostMapping("/events")
    @PermitAll
    fun event(
        @RequestBody fixture: EventFixture,
    ): Long {
        val event =
            Event(
                committee = fixture.committeeId?.let(committees::findById),
                title = fixture.title,
                description = fixture.description,
                location = fixture.location,
                startTime = fixture.startTime,
                endTime = fixture.endTime,
                approved = fixture.approved,
                membersOnly = fixture.membersOnly,
                signUp = fixture.signUp,
                signUpLimit = fixture.signUpLimit,
            )
        return requireNotNull(events.create(event).id)
    }
}
