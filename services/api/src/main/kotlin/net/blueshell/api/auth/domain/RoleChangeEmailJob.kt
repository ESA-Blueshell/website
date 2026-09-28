package net.blueshell.api.auth.domain

import net.blueshell.api.email.api.EmailSenderService
import net.blueshell.api.jobs.api.AbstractJsonJobHandler
import net.blueshell.api.shared.job.requireExists
import net.blueshell.api.user.api.RoleChanges
import net.blueshell.api.user.api.UserJobs
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Tells somebody their admin or board access changed.
 *
 * Here rather than in `user`, which writes the record: `email` depends on `jobs` and `jobs`
 * depends on `user`, so a `user` that mailed would close a cycle. This module already owns the
 * mail an account gets about reaching the site.
 */
@Component
class RoleChangeEmailJob(
    objectMapper: ObjectMapper,
    private val roleChanges: RoleChanges,
    private val emails: EmailSenderService,
    @param:Value($$"${frontend.url}") private val frontendUrl: String,
) : AbstractJsonJobHandler<UserJobs.RoleChangePayload>(
        objectMapper,
        UserJobs.RoleChange,
    ) {
    override fun handlePayload(payload: UserJobs.RoleChangePayload) {
        val change = requireExists { roleChanges.find(payload.roleChangeId) }
        emails.send(createRoleChangeEmail(change, frontendUrl), UserJobs.RoleChange.type, currentExecutionId)
    }
}
