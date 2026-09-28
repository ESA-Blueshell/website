package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChange
import net.blueshell.api.shared.job.JobTrigger

/** What the jobs page says queued a job this module runs for an event change. */
internal fun EventChange.asTrigger(): JobTrigger =
    when (this) {
        EventChange.CREATED -> JobTrigger.EVENT_CREATED
        EventChange.UPDATED -> JobTrigger.EVENT_UPDATED
        EventChange.APPROVED -> JobTrigger.EVENT_APPROVED
        EventChange.UNAPPROVED -> JobTrigger.EVENT_UNAPPROVED
        EventChange.DELETED -> JobTrigger.EVENT_DELETED
    }
