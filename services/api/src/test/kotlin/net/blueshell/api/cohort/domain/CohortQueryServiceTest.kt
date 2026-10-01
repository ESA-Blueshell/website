package net.blueshell.api.cohort.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.blueshell.api.cohort.persistence.Cohort
import net.blueshell.api.cohort.persistence.CohortCategory
import net.blueshell.api.cohort.persistence.CohortRepository
import net.blueshell.api.cohort.persistence.CohortType
import net.blueshell.api.cohort.persistence.DriftResolution
import net.blueshell.api.cohort.persistence.DriftResolutionAction
import net.blueshell.api.cohort.persistence.DriftResolutionRepository
import net.blueshell.api.cohort.persistence.Target
import net.blueshell.api.cohort.persistence.TargetKind
import net.blueshell.api.cohort.persistence.TargetMember
import net.blueshell.api.cohort.persistence.TargetMemberRepository
import net.blueshell.api.cohort.persistence.TargetReconcileRunRepository
import net.blueshell.api.cohort.persistence.TargetRepository
import net.blueshell.api.shared.enums.TargetMemberState
import net.blueshell.api.shared.enums.TargetSystem
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Optional

class CohortQueryServiceTest {
    private val cohorts: CohortRepository = mockk()
    private val targets: TargetRepository = mockk()
    private val targetMembers: TargetMemberRepository = mockk()
    private val users: UserService = mockk()
    private val targetExternalIds: CohortTargetIds = mockk()
    private val definitions: CohortDefinitionRegistry = mockk()
    private val brevo: TargetStrategy =
        mockk<TargetStrategy>().also {
            every { it.system } returns TargetSystem.BREVO
            every { it.makesMemberIds } returns true
            every { it.descriptor } returns
                TargetDescriptor(
                    system = TargetSystem.BREVO,
                    kind = TargetKind.LIST,
                )
        }
    private val strategies: TargetStrategies = TargetStrategies(listOf(brevo))
    private val runs: TargetReconcileRunRepository = mockk(relaxed = true)
    private val resolutions: DriftResolutionRepository = mockk(relaxed = true)
    private val service =
        CohortQueryService(
            cohorts,
            targets,
            targetMembers,
            users,
            targetExternalIds,
            definitions,
            strategies,
            runs,
            resolutions,
        )

    @Test
    fun `summaries returns memberCount and mappingCount from count methods not findAll`() {
        val cohort = cohort(1L)
        every { cohorts.findAll() } returns listOf(cohort)
        every { targetMembers.countByCohortIdAndUserIdIsNotNull(1L) } returns 4L
        every { targets.countByCohortId(1L) } returns 2L

        val result = service.summaries()

        assertThat(result).hasSize(1)
        assertThat(result[0].memberCount).isEqualTo(4)
        assertThat(result[0].mappingCount).isEqualTo(2)
        verify(exactly = 1) { targetMembers.countByCohortIdAndUserIdIsNotNull(1L) }
        verify(exactly = 1) { targets.countByCohortId(1L) }
        verify(exactly = 0) { targetMembers.findAllByCohortIdAndUserIdIsNotNull(any()) }
        verify(exactly = 0) { targets.findAllByCohortId(any()) }
    }

    @Test
    fun `summaries excludes stranger rows (userId == null) from memberCount`() {
        val cohort = cohort(2L)
        every { cohorts.findAll() } returns listOf(cohort)
        // 1 desired member; stranger rows (null userId) are excluded by the count predicate
        every { targetMembers.countByCohortIdAndUserIdIsNotNull(2L) } returns 1L
        every { targets.countByCohortId(2L) } returns 3L

        val result = service.summaries()

        assertThat(result[0].memberCount).isEqualTo(1)
        assertThat(result[0].mappingCount).isEqualTo(3)
    }

    @Test
    fun `targets count our people on each, and a summary browses under its type's category`() {
        every { targets.findAll() } returns listOf(target(5L))
        every { targetMembers.countByTargetIdAndUserIdIsNotNull(5L) } returns 3L
        every { cohorts.findAll() } returns listOf(cohort(6L))
        every { targetMembers.countByCohortIdAndUserIdIsNotNull(6L) } returns 0L
        every { targets.countByCohortId(6L) } returns 1L

        assertThat(service.targets().single().memberCount).isEqualTo(3)
        assertThat(
            service
                .targets()
                .single()
                .target.id,
        ).isEqualTo(5L)
        assertThat(service.summaries().single().category).isEqualTo(CohortCategory.MEMBERS)
    }

    @Test
    fun `summaries returns empty list when no cohorts exist`() {
        every { cohorts.findAll() } returns emptyList()

        val result = service.summaries()

        assertThat(result).isEmpty()
        verify(exactly = 0) { targetMembers.countByCohortIdAndUserIdIsNotNull(any()) }
        verify(exactly = 0) { targets.countByCohortId(any()) }
    }

    @Test
    fun `summaries aggregates counts across multiple cohorts`() {
        val cohortA = cohort(10L)
        val cohortB = cohort(11L)
        every { cohorts.findAll() } returns listOf(cohortA, cohortB)
        every { targetMembers.countByCohortIdAndUserIdIsNotNull(10L) } returns 7L
        every { targetMembers.countByCohortIdAndUserIdIsNotNull(11L) } returns 0L
        every { targets.countByCohortId(10L) } returns 1L
        every { targets.countByCohortId(11L) } returns 2L

        val result = service.summaries()

        assertThat(result).hasSize(2)
        val a = result.first { it.cohort.id == 10L }
        val b = result.first { it.cohort.id == 11L }
        assertThat(a.memberCount).isEqualTo(7)
        assertThat(a.mappingCount).isEqualTo(1)
        assertThat(b.memberCount).isEqualTo(0)
        assertThat(b.mappingCount).isEqualTo(2)
    }

    //
    // The page reads membership and its agreement with the external system from one payload,
    // so every row comes back — including the ones with no local account — carrying the state
    // it is in.

    @Test
    fun `detail reports the state each row is in`() {
        val cohort = cohort(20L)
        val target = target(200L)
        val desired = member(target, cohort, userId = 5L)
        val synced = member(target, cohort, userId = 6L, syncedAt = NOW)
        val verified = member(target, cohort, userId = 7L, syncedAt = NOW, verifiedAt = NOW)
        stubDetail(cohort, target, listOf(desired, synced, verified))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()

        val detail = service.detail(20L)

        // The page reads whether a row is in step with the target off the row itself, rather
        // than from a second call that classifies the same rows again.
        assertThat(detail.members.map { it.state }).containsExactlyInAnyOrder(
            TargetMemberState.DESIRED,
            TargetMemberState.SYNCED,
            TargetMemberState.VERIFIED,
        )
        assertThat(detail.members.map { it.system }.distinct()).containsExactly(TargetSystem.BREVO)
    }

    @Test
    fun `detail returns rows present externally with no local account`() {
        val cohort = cohort(24L)
        val target = target(240L)
        val member = member(target, cohort, userId = 5L)
        val stranger = member(target, cohort, userId = null, externalUserId = "ext-9", verifiedAt = NOW, label = "someone@example.com")
        stubDetail(cohort, target, listOf(member, stranger))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()

        val rows = service.detail(24L).members

        // The old member query filtered these out for having no user, which is the one thing
        // that makes them worth showing.
        assertThat(rows).hasSize(2)
        val strangerRow = rows.single { it.member.userId == null }
        assertThat(strangerRow.state).isEqualTo(TargetMemberState.STRANGER)
        assertThat(strangerRow.member.externalUserId).isEqualTo("ext-9")
        assertThat(strangerRow.member.label).isEqualTo("someone@example.com")
    }

    @Test
    fun `detail marks the people with no account on a system where everybody links their own`() {
        val discord =
            mockk<TargetStrategy>().also {
                every { it.system } returns TargetSystem.DISCORD
                every { it.descriptor } returns TargetDescriptor(system = TargetSystem.DISCORD, kind = TargetKind.ROLE)
                every { it.makesMemberIds } returns false
                every { it.memberIds(setOf(5L, 6L)) } returns mapOf(5L to "900")
                every { it.ownersOf(any()) } returns emptyMap()
            }
        val withDiscord =
            CohortQueryService(
                cohorts,
                targets,
                targetMembers,
                users,
                targetExternalIds,
                definitions,
                TargetStrategies(listOf(discord)),
                runs,
                resolutions,
            )
        val cohort = cohort(26L)
        val role = Target(system = "DISCORD", kind = TargetKind.ROLE, label = "Sitecie").apply { id = 260L }
        val linked = member(role, cohort, userId = 5L).apply { id = 1L }
        val unlinked = member(role, cohort, userId = 6L).apply { id = 2L }
        val stranger = member(role, cohort, userId = null, externalUserId = "901", verifiedAt = NOW).apply { id = 3L }
        stubDetail(cohort, role, listOf(linked, unlinked, stranger))
        stubNoUsers()

        val rows = withDiscord.detail(26L).members.associate { it.member.id to it.unreachable }

        assertThat(rows).isEqualTo(mapOf(1L to false, 2L to true, 3L to false))

        every { discord.memberIds(any()) } throws IllegalStateException("users unreadable")
        assertThat(
            withDiscord
                .detail(26L)
                .members
                .filter { it.unreachable }
                .map { it.member.id },
        ).containsExactlyInAnyOrder(1L, 2L)
    }

    @Test
    fun `detail names the account behind a stranger's external id`() {
        val cohort = cohort(21L)
        val target = target(210L)
        val stranger = member(target, cohort, userId = null, externalUserId = "ext-42", verifiedAt = NOW)
        stubDetail(cohort, target, listOf(stranger))
        every { brevo.ownersOf(setOf("ext-42")) } returns mapOf("ext-42" to 77L)
        every { users.findAllByIds(listOf(77L)) } returns listOf(user(77L, "Emma Dokter"))
        every { users.isSoftDeleted(any()) } returns false

        val row = service.detail(21L).members.single()

        // Without this the page can only show an opaque external id for somebody it knows.
        assertThat(row.resolvedUserId).isEqualTo(77L)
        assertThat(row.user?.fullName).isEqualTo("Emma Dokter")
    }

    @Test
    fun `detail leaves a stranger nameless when no account claims its external id`() {
        val cohort = cohort(25L)
        val target = target(250L)
        stubDetail(cohort, target, listOf(member(target, cohort, userId = null, externalUserId = "ext-unknown", verifiedAt = NOW)))
        every { brevo.ownersOf(any()) } returns emptyMap()
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false

        val row = service.detail(25L).members.single()

        assertThat(row.resolvedUserId).isNull()
        assertThat(row.user).isNull()
    }

    @Test
    fun `detail reports a mapping's newest confirmation as when it was last reconciled`() {
        val cohort = cohort(22L)
        val target = target(220L)
        val older = member(target, cohort, userId = 1L, syncedAt = NOW, verifiedAt = NOW.minusDays(3))
        val newest = member(target, cohort, userId = 2L, syncedAt = NOW, verifiedAt = NOW)
        stubDetail(cohort, target, listOf(older, newest))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()

        val mapping = service.detail(22L).mappings.single()

        assertThat(mapping.lastReconciledAt).isEqualTo(NOW.toInstant(ZoneOffset.UTC))
    }

    @Test
    fun `detail places a mapping under its system only, never the folder its row was created for`() {
        val cohort = cohort(26L)
        val target = target(260L).apply { folder = "Committees" }
        stubDetail(cohort, target, emptyList())
        stubNoUsers()

        val mapping = service.detail(26L).mappings.single()

        // The system named the way an operator sees it; the folder is read from Brevo by the caller.
        assertThat(mapping.path).containsExactly("Brevo")
    }

    @Test
    fun `detail places an unfiled mapping directly under its system`() {
        val cohort = cohort(27L)
        val target = target(270L).apply { folder = null }
        stubDetail(cohort, target, emptyList())
        stubNoUsers()

        // No folder is not an anonymous folder: the path is one step, not two.
        assertThat(
            service
                .detail(27L)
                .mappings
                .single()
                .path,
        ).containsExactly("Brevo")
    }

    @Test
    fun `detail ignores a folder recorded as blank`() {
        val cohort = cohort(28L)
        val target = target(280L).apply { folder = "   " }
        stubDetail(cohort, target, emptyList())
        stubNoUsers()

        assertThat(
            service
                .detail(28L)
                .mappings
                .single()
                .path,
        ).containsExactly("Brevo")
    }

    @Test
    fun `detail still names a system that has no strategy registered`() {
        val cohort = cohort(29L)
        val target =
            Target(system = "GOOGLE_CALENDAR", kind = TargetKind.LIST, label = "Gone")
                .apply { id = 290L }
        stubDetail(cohort, target, emptyList())
        stubNoUsers()

        // A cohort can outlive the adapter that made it. Without a strategy there is no
        // human label to use, so the row falls back to the system's own name rather than
        // losing its place.
        assertThat(
            service
                .detail(29L)
                .mappings
                .single()
                .path,
        ).containsExactly("GOOGLE_CALENDAR")
    }

    @Test
    fun `detail leaves out a cohort pointing at a system this build does not have`() {
        val cohort = cohort(30L)
        val known = target(300L)
        val unknown =
            Target(system = "MASTODON", kind = TargetKind.LIST, label = "Gone")
                .apply { id = 301L }
        every { cohorts.findById(30L) } returns Optional.of(cohort)
        every { targets.findAllByCohortId(30L) } returns listOf(known, unknown)
        every { targetExternalIds.find(known) } returns "external-1"
        every { targetMembers.findAllByCohortId(30L) } returns emptyList()
        every { targetMembers.findAllByTargetId(any()) } returns emptyList()
        stubNoUsers()

        val detail = service.detail(30L)

        // Nothing on that row would work without its system, and one dead row is not worth
        // the whole page.
        assertThat(detail.mappings).extracting<Long> { it.target.id }.containsExactly(300L)
    }

    @Test
    fun `detail still lists the members of a cohort whose system is gone`() {
        val cohort = cohort(31L)
        val unknown =
            Target(system = "MASTODON", kind = TargetKind.LIST, label = "Gone")
                .apply { id = 310L }
        val row = member(unknown, cohort, userId = 1L)
        every { cohorts.findById(31L) } returns Optional.of(cohort)
        every { targets.findAllByCohortId(31L) } returns listOf(unknown)
        every { targetMembers.findAllByCohortId(31L) } returns listOf(row)
        every { targetMembers.findAllByTargetId(any()) } returns listOf(row)
        every { users.findAllByIds(any()) } returns listOf(user(1L, "Emma Dokter"))
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()

        val detail = service.detail(31L)

        // Dropping the target must not drop the people in it: they are still on the ledger,
        // and hiding them would make the page lie about who is in the cohort.
        assertThat(detail.mappings).isEmpty()
        assertThat(detail.members).hasSize(1)
        assertThat(detail.members.single().system).isNull()
    }

    @Test
    fun `detail leaves last reconciled null for a cohort never confirmed`() {
        val cohort = cohort(23L)
        val target = target(230L)
        stubDetail(cohort, target, listOf(member(target, cohort, userId = 1L)))
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()

        assertThat(
            service
                .detail(23L)
                .mappings
                .single()
                .lastReconciledAt,
        ).isNull()
    }

    @Test
    fun `detail names who each recent resolution concerned and who made it`() {
        val cohort = cohort(26L)
        val target = target(260L)
        stubDetail(cohort, target, emptyList())
        val at = Instant.parse("2026-09-29T20:00:00Z")
        every { resolutions.findTop20ByTargetIdInOrderByResolvedAtDesc(setOf(260L)) } returns
            listOf(
                DriftResolution(260L, DriftResolutionAction.PUSH, 5L, null, null, 9L, at),
                DriftResolution(260L, DriftResolutionAction.REMOVE, null, "ext-1", "c@example.com", null, at),
            )
        every { users.findAllByIds(listOf(5L, 9L)) } returns listOf(user(5L, "Ada Lovelace"), user(9L, "Board Member"))
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()

        val rows = service.detail(26L).resolutions

        assertThat(rows.map { it.personName }).containsExactly("Ada Lovelace", "c@example.com")
        assertThat(rows.map { it.resolvedByName }).containsExactly("Board Member", null)
        assertThat(rows.map { it.system }).containsOnly(TargetSystem.BREVO)
    }

    private fun stubNoUsers() {
        every { users.findAllByIds(any()) } returns emptyList()
        every { users.isSoftDeleted(any()) } returns false
        every { brevo.ownersOf(any()) } returns emptyMap()
    }

    private fun stubDetail(
        cohort: Cohort,
        target: Target,
        rows: List<TargetMember>,
    ) {
        every { cohorts.findById(cohort.id!!) } returns Optional.of(cohort)
        every { targets.findAllByCohortId(cohort.id!!) } returns listOf(target)
        every { targetExternalIds.find(target) } returns "external-1"
        every { targetMembers.findAllByCohortId(cohort.id!!) } returns rows
        every { targetMembers.findAllByTargetId(target.id!!) } returns rows
    }

    private fun cohort(id: Long): Cohort = Cohort(CohortType.NEWSLETTER_SUBSCRIBERS, "Test cohort $id").apply { this.id = id }

    private fun target(id: Long): Target = Target(system = "BREVO", kind = TargetKind.LIST, label = "Cohort $id").apply { this.id = id }

    private fun member(
        target: Target,
        cohort: Cohort,
        userId: Long?,
        externalUserId: String? = null,
        syncedAt: LocalDateTime? = null,
        verifiedAt: LocalDateTime? = null,
        label: String? = null,
    ): TargetMember =
        TargetMember(
            target = target,
            userId = userId,
            cohort = cohort,
            externalUserId = externalUserId,
            syncedAt = syncedAt,
            verifiedAt = verifiedAt,
            label = label,
        ).apply { id = nextMemberId++ }

    private fun user(
        id: Long,
        fullName: String,
    ): User =
        Entities.user(
            id = id,
            firstName = fullName.substringBefore(' '),
            lastName = fullName.substringAfter(' '),
            email = "user$id@example.com",
        )

    private companion object {
        val NOW: LocalDateTime = LocalDateTime.of(2026, 3, 1, 12, 0)
        var nextMemberId = 1L
    }
}
