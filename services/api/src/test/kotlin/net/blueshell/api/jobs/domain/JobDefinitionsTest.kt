package net.blueshell.api.jobs.domain

import net.blueshell.api.auth.domain.AuthJobs
import net.blueshell.api.contact.api.ContactJobs
import net.blueshell.api.contribution.domain.ContributionJobs
import net.blueshell.api.event.domain.EventJobs
import net.blueshell.api.file.domain.ImageJobs
import net.blueshell.api.shared.enums.TokenPurpose
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.sync.domain.CalendarJobs
import net.blueshell.api.sync.domain.DiscordPostJobs
import net.blueshell.api.user.api.UserJobs
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Each module defines its own jobs, so only here are they seen side by side. */
class JobDefinitionsTest {
    private class Queued<T : Any>(
        val definition: JobDefinition<T>,
        val payload: T,
    ) {
        val dedupKey: String? get() = definition.dedupKey(payload)
    }

    private val emails =
        listOf(
            Queued(AuthJobs.Recovery, AuthJobs.RecoveryPayload(1, "token", TokenPurpose.PASSWORD_RESET)),
            Queued(
                AuthJobs.SecurityNotification,
                AuthJobs.SecurityNotificationPayload(1, AuthJobs.SecurityNotificationAudience.PERSON),
            ),
            Queued(UserJobs.RoleChange, UserJobs.RoleChangePayload(1)),
            Queued(EventJobs.EventSignup, EventJobs.EventSignupPayload(1, "guest")),
            Queued(EventJobs.EventSignUpRemoved, EventJobs.EventSignUpRemovedPayload("a@example.com", "A", "Event")),
            Queued(ContributionJobs.ContributionReminder, ContributionJobs.ContributionReminderPayload(1)),
            Queued(ContributionJobs.JoiningContribution, ContributionJobs.JoiningContributionPayload(1)),
            Queued(ContributionJobs.IncassoNotification, ContributionJobs.IncassoNotificationPayload(1)),
        )

    private val syncs =
        listOf(
            Queued(ContactJobs.SyncContact, ContactJobs.SyncContactPayload(1)),
            Queued(ContactJobs.RemoveContact, ContactJobs.RemoveContactPayload(1)),
            Queued(DiscordPostJobs.Announcement, DiscordPostJobs.EventPostPayload(1)),
            Queued(DiscordPostJobs.CalendarPost, DiscordPostJobs.EventPostPayload(1)),
            Queued(DiscordPostJobs.DiscordEvent, DiscordPostJobs.EventPostPayload(1)),
            Queued(CalendarJobs.SyncCalendarEvent, CalendarJobs.SyncCalendarEventPayload(1)),
            Queued(ImageJobs.DeriveRenditions, ImageJobs.DeriveRenditionsPayload(1)),
        )

    private val all = emails + syncs + Queued(ContactJobs.SyncAllContacts, ContactJobs.SyncAllContactsPayload())

    @Test
    fun `each definition reads the payload class it is queued with`() {
        all.forEach { assertThat(it.definition.payloadType).isEqualTo(it.payload.javaClass) }
    }

    @Test
    fun `no two definitions share a type`() {
        assertThat(all.map { it.definition.type }).doesNotHaveDuplicates()
    }

    @Test
    fun `an email is sent however many are queued at once`() {
        emails.forEach { assertThat(it.dedupKey).describedAs(it.definition.type).isNull() }
        assertThat(all.last().dedupKey).isNull()
    }

    @Test
    fun `a sync queued twice for the same subject is one job`() {
        syncs.forEach { assertThat(it.dedupKey).describedAs(it.definition.type).isNotNull() }
        assertThat(syncs.filter { it.definition in DISCORD }.map { it.definition.queuesBehindRunning }).containsOnly(true)
    }

    private companion object {
        val DISCORD = setOf<JobDefinition<*>>(DiscordPostJobs.Announcement, DiscordPostJobs.CalendarPost, DiscordPostJobs.DiscordEvent)
    }
}
